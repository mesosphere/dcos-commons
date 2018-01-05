import json
import logging
import uuid

import pytest

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_tasks
import sdk_utils
import sdk_security

from tests import config
from tests import auth
from tests import topics
from tests import test_utils


log = logging.getLogger(__name__)


pytestmark = pytest.mark.skipif(sdk_utils.is_open_dcos(),
                                reason='Feature only supported in DC/OS EE')


@pytest.fixture(scope='module', autouse=True)
def service_account(configure_security):
    """
    Creates service account and yields the name.
    """
    name = config.SERVICE_NAME
    sdk_security.create_service_account(
        service_account_name=name, service_account_secret=name)
    # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
    sdk_cmd.run_cli(
        "security org groups add_user superusers {name}".format(name=name))
    yield name
    sdk_security.delete_service_account(
        service_account_name=name, service_account_secret=name)


@pytest.fixture(scope='module', autouse=True)
def kafka_client():
    brokers = ["kafka-0-broker.{}.autoip.dcos.thisdcos.directory:1030".format(config.SERVICE_NAME),
               "kafka-1-broker.{}.autoip.dcos.thisdcos.directory:1030".format(config.SERVICE_NAME),
               "kafka-2-broker.{}.autoip.dcos.thisdcos.directory:1030".format(config.SERVICE_NAME)]

    try:
        client_id = "kafka-client"
        client = {
            "id": client_id,
            "mem": 512,
            "container": {
                "type": "MESOS",
                "docker": {
                    "image": "elezar/kafka-client:latest",
                    "forcePullImage": True
                },
            },
            "networks": [
                {
                    "mode": "host"
                }
            ],
            "env": {
                "JVM_MaxHeapSize": "512",
                "KAFKA_CLIENT_MODE": "test",
                "KAFKA_BROKER_LIST": ",".join(brokers),
                "KAFKA_OPTS": ""
            }
        }

        sdk_marathon.install_app(client)
        yield {**client,
        **{"brokers": list(map(lambda x: x.split(':')[0], brokers))}}

    finally:
        sdk_marathon.destroy_app(client_id)


@pytest.fixture(scope='module', autouse=True)
def setup_principals(kafka_client):
    client_id = kafka_client["id"]

    create_tls_artifacts(
        cn="kafka-tester",
        task=client_id)
    create_tls_artifacts(
        cn="authorized",
        task=client_id)
    create_tls_artifacts(
        cn="unauthorized",
        task=client_id)
    create_tls_artifacts(
        cn="super",
        task=client_id)


@pytest.mark.dcos_min_version('1.10')
@pytest.mark.ee_only
@pytest.mark.sanity
def test_authn_client_can_read_and_write(kafka_client, service_account, setup_principals):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options={
                "brokers": {
                    "port_tls": 1030
                },
                "service": {
                    "service_account": service_account,
                    "service_account_secret": service_account,
                    "security": {
                        "transport_encryption": {
                            "enabled": True
                        },
                        "ssl_authentication": {
                            "enabled": True
                        }
                    }
                }
            })

        client_id = kafka_client["id"]
        auth.wait_for_brokers(client_id, kafka_client["brokers"])

        sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME,
            "topic create tls.topic",
            json=True)

        test_utils.wait_for_topic(config.PACKAGE_NAME, config.SERVICE_NAME, "tls.topic")

        message = str(uuid.uuid4())

        # Write to the topic
        log.info("Writing and reading: Writing to the topic, with authn")
        assert write_to_topic("kafka-tester", client_id, "tls.topic", message)

        log.info("Writing and reading: reading from the topic, with authn")
        # Read from the topic
        assert message in read_from_topic("kafka-tester", client_id, "tls.topic", 1)
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.10')
@pytest.mark.ee_only
@pytest.mark.sanity
def test_authz_acls_required(kafka_client, service_account, setup_principals):
    client_id = kafka_client["id"]

    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options={
                "brokers": {
                    "port_tls": 1030
                },
                "service": {
                    "service_account": service_account,
                    "service_account_secret": service_account,
                    "security": {
                        "transport_encryption": {
                            "enabled": True
                        },
                        "ssl_authentication": {
                            "enabled": True
                        },
                        "authorization": {
                            "enabled": True,
                            "super_users": "User:{}".format("super")
                        }
                    }
                }
            })

        auth.wait_for_brokers(client_id, kafka_client["brokers"])

        sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME,
            "topic create authz.test",
            json=True)

        test_utils.wait_for_topic(config.PACKAGE_NAME, config.SERVICE_NAME, "authz.test")

        super_message = str(uuid.uuid4())
        authorized_message = str(uuid.uuid4())

        log.info("Writing and reading: Writing to the topic, as authorized user")
        assert not write_to_topic("authorized", client_id, "authz.test", authorized_message)

        log.info("Writing and reading: Writing to the topic, as super user")
        assert write_to_topic("super", client_id, "authz.test", super_message)

        log.info("Writing and reading: Reading from the topic, as authorized user")
        assert auth.is_not_authorized(read_from_topic("authorized", client_id, "authz.test", 1))

        log.info("Writing and reading: Reading from the topic, as super user")
        read_result = read_from_topic("super", client_id, "authz.test", 1)
        assert super_message in read_result and authorized_message not in read_result

        # Add acl
        log.info("Writing and reading: Adding acl for authorized user")
        zookeeper_endpoint = str(sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            "endpoint zookeeper")).strip()
        topics.add_acls("authorized", client_id, "authz.test", zookeeper_endpoint, env_str=None)

        log.info("Writing and reading: Writing and reading as authorized user")
        assert write_to_topic("authorized", client_id, "authz.test", authorized_message)
        assert authorized_message in read_from_topic("authorized", client_id, "authz.test", 2)
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.10')
@pytest.mark.ee_only
@pytest.mark.sanity
def test_authz_acls_not_required(kafka_client, service_account, setup_principals):
    client_id = kafka_client["id"]

    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options={
                "brokers": {
                    "port_tls": 1030
                },
                "service": {
                    "service_account": service_account,
                    "service_account_secret": service_account,
                    "security": {
                        "transport_encryption": {
                            "enabled": True
                        },
                        "ssl_authentication": {
                            "enabled": True
                        },
                        "authorization": {
                            "enabled": True,
                            "super_users": "User:{}".format("super"),
                            "allow_everyone_if_no_acl_found": True
                        }
                    }
                }
            })

        auth.wait_for_brokers(client_id, kafka_client["brokers"])

        # Create the topic
        sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME,
            "topic create authz.test",
            json=True)

        test_utils.wait_for_topic(config.PACKAGE_NAME, config.SERVICE_NAME, "authz.test")

        super_message = str(uuid.uuid4())
        authorized_message = str(uuid.uuid4())
        unauthorized_message = str(uuid.uuid4())

        log.info("Writing and reading: Writing to the topic, as authorized user")
        assert write_to_topic("authorized", client_id, "authz.test", authorized_message)

        log.info("Writing and reading: Writing to the topic, as unauthorized user")
        assert write_to_topic("unauthorized", client_id, "authz.test", unauthorized_message)

        log.info("Writing and reading: Writing to the topic, as super user")
        assert write_to_topic("super", client_id, "authz.test", super_message)

        log.info("Writing and reading: Reading from the topic, as authorized user")
        assert authorized_message in read_from_topic("authorized", client_id, "authz.test", 3)

        log.info("Writing and reading: Reading from the topic, as unauthorized user")
        assert unauthorized_message in read_from_topic("unauthorized", client_id, "authz.test", 3)

        log.info("Writing and reading: Reading from the topic, as super user")
        assert super_message in read_from_topic("super", client_id, "authz.test", 3)

        log.info("Writing and reading: Adding acl for authorized user")
        zookeeper_endpoint = str(sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            "endpoint zookeeper")).strip()
        topics.add_acls("authorized", client_id, "authz.test", zookeeper_endpoint, env_str=None)

        # Re-roll the messages so we really prove auth is in place.
        super_message = str(uuid.uuid4())
        authorized_message = str(uuid.uuid4())
        unauthorized_message = str(uuid.uuid4())

        log.info("Writing and reading: Writing to the topic, as authorized user")
        assert write_to_topic("authorized", client_id, "authz.test", authorized_message)

        log.info("Writing and reading: Writing to the topic, as unauthorized user")
        assert not write_to_topic("unauthorized", client_id, "authz.test", unauthorized_message)

        log.info("Writing and reading: Writing to the topic, as super user")
        assert write_to_topic("super", client_id, "authz.test", super_message)

        log.info("Writing and reading: Reading from the topic, as authorized user")
        read_result = read_from_topic("authorized", client_id, "authz.test", 5)
        assert authorized_message in read_result and unauthorized_message not in read_result

        log.info("Writing and reading: Reading from the topic, as unauthorized user")
        assert auth.is_not_authorized(read_from_topic("unauthorized", client_id, "authz.test", 1))

        log.info("Writing and reading: Reading from the topic, as super user")
        read_result = read_from_topic("super", client_id, "authz.test", 5)
        assert super_message in read_result and unauthorized_message not in read_result
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def create_tls_artifacts(cn: str, task: str) -> str:
    pub_path = "{}_pub.crt".format(cn)
    priv_path = "{}_priv.key".format(cn)
    log.info("Generating certificate. cn={}, task={}".format(cn, task))

    output = sdk_tasks.task_exec(task,
        'openssl req -nodes -newkey rsa:2048 -keyout {} -out request.csr \
        -subj "/C=US/ST=CA/L=SF/O=Mesosphere/OU=Mesosphere/CN={}"'.format(priv_path, cn))
    log.info(output)
    assert output[0] is 0

    raw_csr = sdk_tasks.task_exec(task, 'cat request.csr')
    assert raw_csr[0] is 0
    request = {
        "certificate_request": raw_csr[1] # The actual content is second in the array
    }

    token = sdk_cmd.run_cli("config show core.dcos_acs_token")

    output = sdk_tasks.task_exec(task,
        "curl -L -X POST \
        -H 'Authorization: token={}' \
        leader.mesos/ca/api/v2/sign \
        -d '{}'".format(token, json.dumps(request)))
    log.info(output)
    assert output[0] is 0

    # Write the public cert to the client
    certificate = json.loads(output[1])["result"]["certificate"]
    output = sdk_tasks.task_exec(task, "bash -c \"echo '{}' > {}\"".format(certificate, pub_path))
    log.info(output)
    assert output[0] is 0

    create_keystore_truststore(cn, task)
    return "CN={},OU=Mesosphere,O=Mesosphere,L=SF,ST=CA,C=US".format(cn)

def create_keystore_truststore(cn: str, task: str):
    pub_path = "{}_pub.crt".format(cn)
    priv_path = "{}_priv.key".format(cn)
    keystore_path = "{}_keystore.jks".format(cn)
    truststore_path = "{}_truststore.jks".format(cn)

    log.info("Generating keystore and truststore, task:{}".format(task))
    output = sdk_tasks.task_exec(task, "curl -L -k -v leader.mesos/ca/dcos-ca.crt -o dcos-ca.crt")

    # Convert to a PKCS12 key
    output = sdk_tasks.task_exec(task,
        'bash -c "export RANDFILE=/mnt/mesos/sandbox/.rnd && \
        openssl pkcs12 -export -in {} -inkey {} \
        -out keypair.p12 -name keypair -passout pass:export \
        -CAfile dcos-ca.crt -caname root"'.format(pub_path, priv_path))
    log.info(output)
    assert output[0] is 0

    log.info("Generating certificate: importing into keystore and truststore")
    # Import into the keystore and truststore
    output = sdk_tasks.task_exec(task,
        "keytool -importkeystore \
        -deststorepass changeit -destkeypass changeit -destkeystore {} \
        -srckeystore keypair.p12 -srcstoretype PKCS12 -srcstorepass export \
        -alias keypair".format(keystore_path))
    log.info(output)
    assert output[0] is 0

    output = sdk_tasks.task_exec(task,
        "keytool -import -trustcacerts -noprompt \
        -file dcos-ca.crt -storepass changeit \
        -keystore {}".format(truststore_path))
    log.info(output)
    assert output[0] is 0


def write_client_properties(cn: str, task: str) -> str:
    sdk_tasks.task_exec(task,
    """bash -c \"cat >{cn}-client.properties << EOL
security.protocol = SSL
ssl.truststore.location = {cn}_truststore.jks
ssl.truststore.password = changeit
ssl.keystore.location = {cn}_keystore.jks
ssl.keystore.password = changeit
EOL\"""".format(cn=cn))

    return "{}-client.properties".format(cn)


def write_to_topic(cn: str, task: str, topic: str, message: str) -> str:
    client_properties = write_client_properties(cn, task)
    cmd = "bash -c \"echo {} | kafka-console-producer \
            --topic {} \
            --producer.config {} \
            --broker-list \$KAFKA_BROKER_LIST\"".format(message,
                                                        topic,
                                                        client_properties)

    return auth.write_to_topic(cn, task, topic, message, cmd=cmd)


def read_from_topic(cn: str, task: str, topic: str, messages: int) -> str:
    client_properties = write_client_properties(cn, task)
    timeout_ms = 60000
    cmd = "bash -c \"kafka-console-consumer \
            --topic {} \
            --consumer.config {} \
            --bootstrap-server \$KAFKA_BROKER_LIST \
            --from-beginning --max-messages {} \
            --timeout-ms {} \
            \"".format(topic, client_properties, messages, timeout_ms)

    return auth.read_from_topic(cn, task, topic, messages, cmd)
