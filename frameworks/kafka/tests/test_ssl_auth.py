import logging
import pytest
import subprocess
import uuid
import json
import time
import shakedown

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_tasks
import sdk_utils
import sdk_security

from tests import config
from tests import auth

log = logging.getLogger(__name__)

# @pytest.fixture(scope='module', autouse=True)
# def service_account(configure_security):
#     """
#     Creates service account and yields the name.
#     """
#     name = config.SERVICE_NAME
#     sdk_security.create_service_account(
#         service_account_name=name, service_account_secret=name)
#     # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
#     sdk_cmd.run_cli(
#         "security org groups add_user superusers {name}".format(name=name))
#     yield name
#     sdk_security.delete_service_account(
#         service_account_name=name, service_account_secret=name)


@pytest.fixture(scope='module', autouse=True)
def kafka_client():
    brokers = ["kafka-0-broker.kafka.autoip.dcos.thisdcos.directory:1030",
               "kafka-1-broker.kafka.autoip.dcos.thisdcos.directory:1030",
               "kafka-2-broker.kafka.autoip.dcos.thisdcos.directory:1030"]

    try:
        client_id = "kafka-client"
        client = {
            "id": client_id,
            "mem": 512,
            "user": "nobody",
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


@pytest.mark.dcos_min_version('1.10')
@pytest.mark.ee_only
@pytest.mark.sanity
def test_authn_client_can_read_and_write(kafka_client, service_account):
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
                        "ssl_auth": {
                            "enable_authentication": True
                        }
                    }
                }
            })

        client_id = kafka_client["id"]
        log.info("Running bootstrap to wait for DNS resolution")
        bootstrap_cmd = ['/opt/bootstrap', '-resolve-hosts', ','.join(kafka_client['brokers']), '-verbose']
        bootstrap_output = sdk_tasks.task_exec(client_id, ' '.join(bootstrap_cmd))
        log.info(bootstrap_output)

        create_tls_artifacts("kafka-tester", client_id)

        message = str(uuid.uuid4())

        # Write to the topic
        log.info("Writing and reading: Writing to the topic, with authn")
        assert ">>" in write_to_topic("kafka-tester", client_id, "tls.topic", message)

        log.info("Writing and reading: reading from the topic, with authn")
        # Read from the topic
        assert message in read_from_topic("kafka-tester", client_id, "tls.topic", 1)
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def test_authz_acls_required(kafka_client):
    client_id = kafka_client["id"]
    # Reconfigure to have authz enabled
    # First, create certs  for super, authorized, and unauthorized
    authorized = create_tls_artifacts(
        cn="authorized",
        task=client_id)
    unauthorized = create_tls_artifacts(
        cn="unauthorized",
        task=client_id)
    super_principal = create_tls_artifacts(
        cn="super",
        task=client_id)

    # try:
        # sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        # config.install(
        #     config.PACKAGE_NAME,
        #     config.SERVICE_NAME,
        #     config.DEFAULT_BROKER_COUNT,
        #     additional_options={
        #         "brokers": {
        #             "port_tls": 1030
        #         },
        #         "service": {
        #             "service_account": service_account,
        #             "service_account_secret": service_account,
        #             "security": {
        #                 "transport_encryption": {
        #                     "enabled": True
        #                 },
        #                 "ssl_auth": {
        #                     "enable_authentication": True
        #                 },
        #                 "authorization": {
        #                     "enabled": True,
        #                     "super_users": "User:{}".format("super")
        #                 }
        #             }
        #         }
        #     })

    auth.wait_for_brokers(client_id, kafka_client["brokers"])

    message = str(uuid.uuid4())

    log.info("Writing and reading: Writing to the topic, but not super user")
    assert "Not authorized to access topics: [authz.test]" in write_to_topic("authorized", client_id, "authz.test", message)

    log.info("Writing and reading: Writing to the topic, as super user")
    assert ">>" in write_to_topic("super", client_id, "authz.test", message)

    log.info("Writing and reading: Reading from the topic, but not super user")
    assert "Not authorized to access topics: [authz.test]" in read_from_topic("authorized", client_id, "authz.test", 1)
    
    log.info("Writing and reading: Reading from the topic, as super user")
    assert message in read_from_topic("super", client_id, "authz.test", 1)
    # finally:
    #     sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


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

    cmd = "curl -X POST \
        -H 'Authorization: token={}' \
        leader.mesos/ca/api/v2/sign \
        -d '{}'".format(token, json.dumps(request))

    output = sdk_tasks.task_exec(task,
        "curl -X POST \
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
    output = sdk_tasks.task_exec(task, "curl -k -v leader.mesos/ca/dcos-ca.crt -o dcos-ca.crt")

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
    output = sdk_tasks.task_exec(task,
        "bash -c \"echo {} | kafka-console-producer \
        --topic {} \
        --producer.config {} \
        --broker-list \$KAFKA_BROKER_LIST\"".format(message, topic, write_client_properties(cn, task)))
    log.info(output)
    assert output[0] is 0
    return " ".join(str(o) for o in output)


def read_from_topic(cn: str, task: str, topic: str, messages: int) -> str:
    output = sdk_tasks.task_exec(task,
        "bash -c \"kafka-console-consumer \
        --topic {} --from-beginning --max-messages {} \
        --timeout-ms 10000 \
        --consumer.config {} \
        --bootstrap-server \$KAFKA_BROKER_LIST\"".format(topic, messages, write_client_properties(cn, task)))
    log.info(output)
    assert output[0] is 0
    return " ".join(str(o) for o in output)
