import logging
import json
import tempfile
import pytest
import uuid

import sdk_auth
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_utils

from tests import auth
from tests import config
from tests import test_utils


log = logging.getLogger(__name__)


pytestmark = [
    pytest.mark.skipif(sdk_utils.dcos_version_less_than('1.10'), reason="Kerberos tests require DC/OS 1.10"),
    pytest.mark.skipif(sdk_utils.is_open_dcos(), reason="Kerberos tests required DC/OS EE"),
]


MESSAGES = []


@pytest.fixture(scope='module', autouse=True)
def kerberos():
    """
    A pytest fixture that installs and configures a KDC used for testing.

    On teardown, the KDC application is removed.
    """
    try:
        kerberos_env = sdk_auth.KerberosEnvironment()

        principals = auth.get_service_principals(config.SERVICE_NAME,
                                                 kerberos_env.get_realm())
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module', autouse=True)
def kafka_server():
    """
    A pytest fixture that installs a non-kerberized kafka service.

    On teardown, the service is uninstalled.
    """
    service_kerberos_options = {
        "service": {
            "name": config.SERVICE_NAME
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

        yield {**service_kerberos_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def kafka_client(kerberos, kafka_server):
    """
    A pytest fixture to install a Kerberized Kafka client as a Marathon application.

    On teardown, the client is uninstalled.
    """
    brokers = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint broker", json=True)["dns"]

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


def test_default_installation(kafka_client, kafka_server):
    assert check_brokers_are_unchanged(kafka_client, kafka_server)
    write_success, read_success = client_can_read_and_write("default",
                                                            kafka_client, kafka_server, None)

    assert write_success, "Write failed"
    assert read_success, "Read failed: MESSAGES={} read_success={}".format(MESSAGES, read_success)


def test_enable_kerberos(kafka_client, kafka_server, kerberos):
    kerberos_options = {
        "service": {
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {
                        "hostname": kerberos.get_host(),
                        "port": int(kerberos.get_port())
                    },
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        }
    }

    with tempfile.NamedTemporaryFile("w", suffix=".kerberos.json") as f:
        kerberos_options_path = f.name

        log.info("Writing kerberos options to %s", kerberos_options_path)
        json.dump(kerberos_options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(kerberos_options_path)]
        sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"], " ".join(cmd))

        # An update plan is a deploy plan
        sdk_plan.wait_for_kicked_off_deployment(kafka_server["service"]["name"])
        sdk_plan.wait_for_completed_deployment(kafka_server["service"]["name"])

    assert check_brokers_are_unchanged(kafka_client, kafka_server)

    # TODO(elezar): The "id" here is currently tied to the principal used. Therefore we need to use "client"
    # for the kerberos test.
    write_success, read_success = client_can_read_and_write("client", kafka_client, kafka_server, kerberos)

    assert write_success, "Write failed"
    assert read_success, "Read failed: MESSAGES={} read_success={}".format(MESSAGES, read_success)


def test_disable_kerberos(kafka_client, kafka_server):
    kerberos_options = {
        "service": {
            "security": {
                "kerberos": {
                    "enabled": False,
                }
            }
        }
    }

    with tempfile.NamedTemporaryFile("w", suffix=".disable-kerberos.json") as f:
        kerberos_options_path = f.name

        log.info("Writing kerberos options to %s", kerberos_options_path)
        json.dump(kerberos_options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(kerberos_options_path)]
        sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"], " ".join(cmd))

        # An update plan is a deploy plan
        sdk_plan.wait_for_kicked_off_deployment(kafka_server["service"]["name"])
        sdk_plan.wait_for_completed_deployment(kafka_server["service"]["name"])

    assert check_brokers_are_unchanged(kafka_client, kafka_server)
    write_success, read_success = client_can_read_and_write("disable_kerberos",
                                                            kafka_client, kafka_server, None)

    assert write_success, "Write failed"
    assert read_success, "Read failed: MESSAGES={} read_success={}".format(MESSAGES, read_success)


def check_brokers_are_unchanged(kafka_client: dict, kafka_server: dict) -> bool:
    brokers = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint broker", json=True)["dns"]

    return set(brokers) == set(kafka_client["env"]["KAFKA_BROKER_LIST"].split(","))


def client_can_read_and_write(test_id: str,
                              kafka_client: dict, kafka_server: dict, krb5=None) -> tuple:
    client_id = kafka_client["id"]

    auth.wait_for_brokers(kafka_client["id"], kafka_client["brokers"])

    topic_name = kafka_client["env"]["KAFKA_TOPIC"]
    sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                    "topic create {}".format(topic_name),
                    json=True)

    test_utils.wait_for_topic(kafka_server["package_name"], kafka_server["service"]["name"], topic_name)

    message = str(uuid.uuid4())

    write_success = write_to_topic(test_id, client_id, topic_name, message, krb5)
    if write_success:
        MESSAGES.append(message)

    read_messages = read_from_topic(test_id, client_id, topic_name, len(MESSAGES), krb5)

    read_success = map(lambda m: m in read_messages, MESSAGES)

    return write_success, read_success


def get_settings(cn: str, task: str, krb5: object) -> tuple:

    if krb5 is None:
        return [], None

    properties = auth.get_kerberos_client_properties(ssl_enabled=False)
    environment = auth.setup_krb5_env(cn, task, krb5)

    return properties, environment


def write_to_topic(cn: str, task: str, topic: str, message: str, krb5: object) -> bool:

    properties, environment = get_settings(cn, task, krb5)
    return auth.write_to_topic(cn, task, topic, message, properties, environment)


def read_from_topic(cn: str, task: str, topic: str, messages: int, krb5: object) -> str:

    properties, environment = get_settings(cn, task, krb5)
    return auth.read_from_topic(cn, task, topic, messages, properties, environment)
