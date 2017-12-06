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
from tests import topics

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

    brokers = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint broker", json=True)["dns"]

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
def test_authz_acls_required(kafka_client, kafka_server):
    client_id = kafka_client["id"]

    auth.wait_for_brokers(kafka_client["id"], kafka_client["brokers"])

    topic_name = "authz.test"

    message = str(uuid.uuid4())

    log.info("Writing and reading: Writing to the topic, but not super user")
    assert "Not authorized to access topics: [authz.test]" in write_to_topic("authorized", client_id, topic_name, message)

    log.info("Writing and reading: Writing to the topic, as super user")
    assert ">>" in write_to_topic("super", client_id, topic_name, message)

    log.info("Writing and reading: Reading from the topic, but not super user")
    assert "Not authorized to access topics: [authz.test]" in read_from_topic("authorized", client_id, topic_name, 1)

    log.info("Writing and reading: Reading from the topic, as super user")
    assert message in read_from_topic("super", client_id, topic_name, 1)

    try:

        zookeeper_endpoint = sdk_cmd.svc_cli(
            kafka_server["package_name"],
            kafka_server["service"]["name"],
            "endpoint zookeeper").strip()

        # TODO: If zookeeper has Kerberos enabled, then the environment should be changed
        topics.add_acls("authorized", client_id, topic_name, zookeeper_endpoint, env_str=None)

        second_message = str(uuid.uuid4())
        log.info("Writing and reading: Writing to the topic, but not super user")
        assert ">>" in write_to_topic("authorized", client_id, topic_name, second_message)

        log.info("Writing and reading: Writing to the topic, as super user")
        assert ">>" in write_to_topic("super", client_id, topic_name, second_message)

        log.info("Writing and reading: Reading from the topic, but not super user")
        topic_output = read_from_topic("authorized", client_id, topic_name, 3)
        assert message in topic_output
        assert second_message in topic_output

        log.info("Writing and reading: Reading from the topic, as super user")
        topic_output = read_from_topic("super", client_id, topic_name, 3)
        assert message in topic_output
        assert second_message in topic_output

        log.info("Writing and reading: Writing to the topic, but not super user")
        assert "Not authorized to access topics: [authz.test]" in write_to_topic("unauthorized", client_id, topic_name, second_message)

        log.info("Writing and reading: Reading from the topic, but not super user")
        assert "Not authorized to access topics: [authz.test]" in read_from_topic("unauthorized", client_id, topic_name, 1)

    except Exception as e:

        log.error("%s", e)
        while True:
            log.info("Sleeping for 30s...")
            time.sleep(30)


def write_client_properties(primary: str, task: str) -> str:
    output_file = "{primary}-client.properties".format(primary=primary)
    log.info("Generating %s", output_file)

    output_cmd = """bash -c \"cat >{output_file} << EOL
security.protocol=SASL_PLAINTEXT
sasl.mechanism=GSSAPI
sasl.kerberos.service.name=kafka
EOL\"""".format(output_file=output_file, primary=primary)
    log.info("Running: %s", output_cmd)
    output = sdk_tasks.task_exec(task, output_cmd)
    log.info(output)

    return output_file


def write_jaas_config_file(primary: str, task: str) -> str:
    output_file = "{primary}-client-jaas.config".format(primary=primary)

    log.info("Generating %s", output_file)

    # TODO: use kafka_client keytab path
    output_cmd = """bash -c \"cat >{output_file} << EOL
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
EOL\"""".format(output_file=output_file, primary=primary)
    log.info("Running: %s", output_cmd)
    output = sdk_tasks.task_exec(task, output_cmd)
    log.info(output)

    return output_file


def write_krb5_config_file(task: str) -> str:
    output_file = "krb5.config"

    log.info("Generating %s", output_file)

    # TODO: Set realm and kdc properties
    output_cmd = """bash -c \"cat >{output_file} << EOL
[libdefaults]
default_realm = LOCAL

[realms]
  LOCAL = {{
    kdc = kdc.marathon.autoip.dcos.thisdcos.directory:2500
  }}
EOL\"""".format(output_file=output_file)
    log.info("Running: %s", output_cmd)
    output = sdk_tasks.task_exec(task, output_cmd)
    log.info(output)

    return output_file


def setup_env(primary: str, task: str) -> str:
    env_setup_string = "export KAFKA_OPTS=\\\"" \
                       "-Djava.security.auth.login.config={} " \
                       "-Djava.security.krb5.conf={}" \
                       "\\\"".format(write_jaas_config_file(primary, task), write_krb5_config_file(task))
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
