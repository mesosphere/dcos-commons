"""
This module tests the interaction of Kafka with Zookeeper with authentication enabled
"""
import logging
import uuid
import pytest

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_utils

from tests import active_directory
from tests import auth
from tests import config
from tests import test_utils


pytestmark = pytest.mark.skipif(not active_directory.is_active_directory_enabled(),
                                reason="This test requires TESTING_ACTIVE_DIRECTORY_SERVER to be set")


log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security):
    try:
        kerberos_env = active_directory.ActiveDirectoryKerberos()
        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module')
def zookeeper_server(kerberos):
    service_kerberos_options = {
        "service": {
            "name": config.ZOOKEEPER_SERVICE_NAME,
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

    try:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        sdk_install.install(
            config.ZOOKEEPER_PACKAGE_NAME,
            config.ZOOKEEPER_SERVICE_NAME,
            config.ZOOKEEPER_TASK_COUNT,
            additional_options=service_kerberos_options,
            timeout_seconds=30 * 60)

        yield {**service_kerberos_options, **{"package_name": config.ZOOKEEPER_PACKAGE_NAME}}

    finally:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def kafka_server(kerberos, zookeeper_server):

    # Get the zookeeper DNS values
    zookeeper_dns = sdk_cmd.svc_cli(zookeeper_server["package_name"],
                                    zookeeper_server["service"]["name"],
                                    "endpoint clientport", json=True)["dns"]

    service_kerberos_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "security": {
                "kerberos": {
                    "enabled": True,
                    "enabled_for_zookeeper": True,
                    "kdc": {
                        "hostname": kerberos.get_host(),
                        "port": int(kerberos.get_port())
                    },
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        },
        "kafka": {
            "kafka_zookeeper_uri": ",".join(zookeeper_dns)
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
                    "image": "elezar/kafka-client:4b9c060",
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
def test_client_can_read_and_write(kafka_client, kafka_server, kerberos):
    client_id = kafka_client["id"]

    sdk_cmd.resolve_hosts(kafka_client["id"], kafka_client["brokers"])

    topic_name = "authn.test"
    sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                    "topic create {}".format(topic_name),
                    json=True)

    test_utils.wait_for_topic(kafka_server["package_name"], kafka_server["service"]["name"], topic_name)

    message = str(uuid.uuid4())

    assert write_to_topic("client", client_id, topic_name, message, kerberos)

    assert message in read_from_topic("client", client_id, topic_name, 1, kerberos)


def write_to_topic(cn: str, task: str, topic: str, message: str, krb5: object) -> bool:

    return auth.write_to_topic(cn, task, topic, message,
                               auth.get_kerberos_client_properties(ssl_enabled=False),
                               auth.setup_krb5_env(cn, task, krb5))


def read_from_topic(cn: str, task: str, topic: str, message: str, krb5: object) -> str:

    return auth.read_from_topic(cn, task, topic, message,
                                auth.get_kerberos_client_properties(ssl_enabled=False),
                                auth.setup_krb5_env(cn, task, krb5))
