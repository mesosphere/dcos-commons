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

log = logging.getLogger(__name__)

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
def configure_package(service_account):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options={
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

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def kafka_client(configure_package):
    brokers = sdk_cmd.svc_cli(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        "endpoint broker-tls", json=True)["dns"]

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
        yield {**client, **{"brokers": list(map(lambda x: x.split(':')[0], brokers))}}

    finally:
        sdk_marathon.destroy_app(client_id)


@pytest.mark.dcos_min_version('1.10')
@pytest.mark.sanity
def test_client_can_read_and_write(kafka_client):
    log.info("Running bootstrap to wait for DNS resolution")
    bootstrap_cmd = ['/opt/bootstrap', '-resolve-hosts', ','.join(kafka_client['brokers']), '-verbose']
    bootstrap_output = sdk_tasks.task_exec(kafka_client['id'], ' '.join(bootstrap_cmd))
    log.info(bootstrap_output)

    create_signed_cert("kafka-tester", kafka_client["id"], "pub.crt", "priv.key")
    create_keystore_truststore(kafka_client["id"], "pub.crt", "priv.key", "keystore.jks", "truststore.jks")

    output = sdk_tasks.task_exec(kafka_client['id'],
    """bash -c \"cat >tls-client.properties << EOL
security.protocol = SSL
ssl.truststore.location = truststore.jks
ssl.truststore.password = changeit
ssl.keystore.location = keystore.jks 
ssl.keystore.password = changeit
EOL\"""")

    message = uuid.uuid4()

    # Write to the topic
    log.info("Writing and reading: Writing to the topic, with authn")
    output = sdk_tasks.task_exec(kafka_client['id'],
        "bash -c \"echo {} | kafka-console-producer \
        --topic tls.topic \
        --producer.config tls-client.properties \
        --broker-list \$KAFKA_BROKER_LIST\"".format(message))
    log.info(output)
    assert output[0] is 0
    assert ">>" in " ".join(str(o) for o in output)

    log.info("Writing and reading: reading from the topic, with authn")
    # Read from the topic
    output = sdk_tasks.task_exec(kafka_client['id'],
        "bash -c \"kafka-console-consumer \
        --topic tls.topic --from-beginning --max-messages 1 \
        --timeout-ms 10000 \
        --consumer.config tls-client.properties \
        --bootstrap-server \$KAFKA_BROKER_LIST\"")
    log.info(output)
    assert output[0] is 0
    assert str(message) in " ".join(str(o) for o in output)


def create_signed_cert(cn: str, task: str, pub_path: str, priv_path: str) -> str:
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

    return "CN={},OU=Mesosphere,O=Mesosphere,L=SF,ST=CA,C=US".format(cn)

def create_keystore_truststore(task: str, pub_path: str, priv_path: str, keystore_path: str, truststore_path: str):
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
