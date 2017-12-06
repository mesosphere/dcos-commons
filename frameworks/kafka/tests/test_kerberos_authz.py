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

from tests import auth
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def kafka_principals():
    fqdn = "{service_name}.{host_suffix}".format(service_name=config.SERVICE_NAME,
                                                 host_suffix=sdk_hosts.AUTOIP_HOST_SUFFIX)

    brokers = [
        "kafka-0-broker",
        "kafka-1-broker",
        "kafka-2-broker",
    ]

    principals = []
    for b in brokers:
        principals.append("kafka/{instance}.{domain}@{realm}".format(
            instance=b,
            domain=fqdn,
            realm=sdk_auth.REALM))

    clients = [
        "client",
        "authorized",
        "unauthorized",
        "super"
    ]
    for c in clients:
        principals.append("{client}@{realm}".format(client=c, realm=sdk_auth.REALM))

    yield principals


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security, kafka_principals):
    try:
        principals = []
        principals.extend(kafka_principals)

        kerberos_env = sdk_auth.KerberosEnvironment()
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module', autouse=True)
def kafka_server(kerberos):
    """
    A pytest fixture that installs a Kerberized kafka service.

    On teardown, the service is uninstalled.
    """

    super_principal = "super"

    service_kerberos_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc_host_name": kerberos.get_host(),
                    "kdc_host_port": int(kerberos.get_port()),
                    "keytab_secret": kerberos.get_keytab_path(),
                },
                "authorization": {
                    "enabled": True,
                    "super_users": "User:{}".format(super_principal)
                }
            }
        },
        "brokers": {
            "port": 1030
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_kerberos_options,
            timeout_seconds=30 * 60)

        yield {**service_kerberos_options, **{"package_name": config.PACKAGE_NAME,
                                              "super_principal": super_principal}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def kafka_client(kerberos, kafka_server):

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
                "volumes": [
                    {
                        "containerPath": "/tmp/kafkaconfig/kafka-client.keytab",
                        "secret": "kafka_keytab"
                    }
                ]
            },
            "secrets": {
                "kafka_keytab": {
                    "source": kerberos.get_keytab_path(),

                }
            },
            "networks": [
                {
                    "mode": "host"
                }
            ],
            "env": {
                "JVM_MaxHeapSize": "512",
                "KAFKA_CLIENT_MODE": "test",
                "KAFKA_TOPIC": "securetest",
                "KAFKA_BROKER_LIST": ",".join(brokers)
            }
        }

        sdk_marathon.install_app(client)
        yield {**client, **{"brokers": list(map(lambda x: x.split(':')[0], brokers))}}

    finally:
        sdk_marathon.destroy_app(client_id)


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_authz_acls_required(kafka_client):
    client_id = kafka_client["id"]

    auth.wait_for_brokers(kafka_client["id"], kafka_client["brokers"])
    message = str(uuid.uuid4())

    log.info("Writing and reading: Writing to the topic, but not super user")
    assert "Not authorized to access topics: [authz.test]" in write_to_topic("authorized", client_id, "authz.test", message)

    log.info("Writing and reading: Writing to the topic, as super user")
    assert ">>" in write_to_topic("super", client_id, "authz.test", message)

    log.info("Writing and reading: Reading from the topic, but not super user")
    assert "Not authorized to access topics: [authz.test]" in read_from_topic("authorized", client_id, "authz.test", 1)

    log.info("Writing and reading: Reading from the topic, as super user")
    assert message in read_from_topic("super", client_id, "authz.test", 1)


def write_client_properties(cn: str, task: str) -> str:
#     sdk_tasks.task_exec(task,
#     """bash -c \"cat >{cn}-client.properties << EOL
# security.protocol = SSL
# ssl.truststore.location = {cn}_truststore.jks
# ssl.truststore.password = changeit
# ssl.keystore.location = {cn}_keystore.jks
# ssl.keystore.password = changeit
# EOL\"""".format(cn=cn))

#     return "{}-client.properties".format(cn)
    return "/tmp/kafkaconfig/client.properties"


def write_jaas_config_file(primary: str, task: str) -> str:
    jaas_config_file = "{primary}-client-jaas.config".format(primary=primary)

    log.info("Generating %s", jaas_config_file)

    # TODO: use kafka_client keytab path
    jaas_cmd = """bash -c \"cat >{file} << EOL
KafkaClient {{
    com.sun.security.auth.module.Krb5LoginModule required
    doNotPrompt=true
    useTicketCache=true
    principal=\\"{primary}@LOCAL\\"
    useKeyTab=true
    serviceName=\\"kafka\\"
    keyTab=\\"/tmp/kafkaconfig/kafka-client.keytab\\"
client=true;
}};
EOL\"""".format(file=jaas_config_file, primary=primary)
    log.info("Running: %s", jaas_cmd)

    output = sdk_tasks.task_exec(task, jaas_cmd)
    log.info(output)

    return jaas_config_file


def setup_env(primary: str, task: str) -> str:
    env_setup_string = "export KAFKA_OPTS=\\\"-Djava.security.auth.login.config={} -Djava.security.krb5.conf=/tmp/kafkaconfig/krb5.conf\\\"".format(write_jaas_config_file(primary, task))
    log.info("Setting environment to %s", env_setup_string)
    return env_setup_string


def write_to_topic(cn: str, task: str, topic: str, message: str) -> str:
    env_str = setup_env(cn, task)

    write_cmd = "bash -c \"{} && echo {} | kafka-console-producer \
        --topic {} \
        --producer.config {} \
        --broker-list \$KAFKA_BROKER_LIST\"".format(env_str, message, topic, write_client_properties(cn, task))

    log.info("Running: %s", write_cmd)
    output = sdk_tasks.task_exec(task, write_cmd)
    log.info(output)
    assert output[0] is 0
    return " ".join(str(o) for o in output)


def read_from_topic(cn: str, task: str, topic: str, messages: int) -> str:
    env_str = setup_env(cn, task)
    read_cmd = "bash -c \"{} && kafka-console-consumer \
        --topic {} --from-beginning --max-messages {} \
        --timeout-ms 10000 \
        --consumer.config {} \
        --bootstrap-server \$KAFKA_BROKER_LIST\"".format(env_str, topic, messages, write_client_properties(cn, task))
    log.info("Running: %s", read_cmd)
    output = sdk_tasks.task_exec(task, read_cmd)
    log.info(output)
    assert output[0] is 0
    return " ".join(str(o) for o in output)
