import logging
import pytest
import subprocess
import uuid
import json

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
        pass


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

    log.info("Generating certificate")
    token = sdk_cmd.run_cli("config show core.dcos_acs_token")
    log.info("Generating certificate: generating CSR")
    output = sdk_tasks.task_exec(kafka_client['id'],
        'openssl req -nodes -newkey rsa:2048 -keyout priv.key -out request.csr \
        -subj "/C=US/ST=CA/L=SF/O=Mesosphere/OU=Mesosphere/CN=kafka-tester"')
    assert output[0] is 0
    
    log.info("Generating certificate: fetching CSR")
    raw_csr = sdk_tasks.task_exec(kafka_client['id'], 'cat request.csr')
    assert raw_csr[0] is 0
    request = {
        "certificate_request": raw_csr[1] # The actual content is second in the array
    }
    log.info("Generating certificate: generated certificate request: {}".format(request))

    # output = sdk_tasks.task_exec(kafka_client['id'], 'bash -c "echo \'{}\' > request.json"'.format(json.dumps(request)))
    # output = sdk_tasks.task_exec(kafka_client['id'], "cat request.json".format(json.dumps(request)))
    cmd = "curl -X POST \
        -H 'Authorization: token={}' \
        leader.mesos/ca/api/v2/sign \
        -d '{}'".format(token, json.dumps(request))

    log.info("Generating certificate: issuing request")
    output = sdk_tasks.task_exec(kafka_client['id'],
        "curl -X POST \
        -H 'Authorization: token={}' \
        leader.mesos/ca/api/v2/sign \
        -d '{}'".format(token, json.dumps(request)))
    assert output[0] is 0

    # Write the public cert to the client
    certificate = json.loads(output[1])["result"]["certificate"]
    output = sdk_tasks.task_exec(kafka_client['id'], "bash -c \"echo '{}' > pub.crt\"".format(certificate))
    assert output[0] is 0

    # Convert to a PKCS12 key
    output = sdk_tasks.task_exec(kafka_client['id'],
        'bash -c "export RANDFILE=/mnt/mesos/sandbox/.rnd && \
        openssl pkcs12 -export -in pub.crt -inkey priv.key \
        -out keypair.p12 -name keypair -passout pass:export \
        -CAfile /run/dcos/pki/CA/ca-bundle.crt -caname root"')
    assert output[0] is 0
    
    # Import into the keystore and truststore
    output = sdk_tasks.task_exec(kafka_client['id'],
        "keytool -importkeystore \
        -deststorepass changeit -destkeypass changeit -destkeystore /tmp/keystore.jks \
        -srckeystore /tmp/keypair.p12 -srcstoretype PKCS12 -srcstorepass export \
        -alias keypair")
    assert output[0] is 0

    output = sdk_tasks.task_exec(kafka_client['id'],
        "-import -trustcacerts -noprompt \
        -file /run/dcos/pki/CA/ca-bundle.crt -storepass changeit \
        -keystore /tmp/truststore.jks")
    assert output[0] is 0

    # Write the client properties
    output = sdk_tasks.task_exec(kafka_client['id'],
        """bash -c \"cat >/tmp/tls-client.properties << EOL
security.protocol = SSL
ssl.truststore.location = /tmp/truststore.jks
ssl.truststore.password = changeit
ssl.keystore.location = /tmp/keystore.jks 
ssl.keystore.password = changeit
EOL\"""")

    # Write to the topic
    output = sdk_tasks.task_exec(kafka_client['id'],
        "echo test | kafka-console-producer \
        --broker-list $(KAFKA_BROKER_LIST) \
        --topic tls.topic \
        --producer.config /tmp/tls-client.properties")

    # Read from the topic
    output = sdk_tasks.task_exec(kafka_client['id'],
        "kafka-console-consumer \
        --bootstrap-server ${KAFKA_BROKER_LIST} \
        --topic securetest --from-beginning --max-messages 1 \
        --timeout-ms 10000 \
        --consumer.config /tmp/tls-client.properties")
    log.info(output)
