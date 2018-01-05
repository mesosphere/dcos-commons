import logging
import pytest
import uuid

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_utils

from tests import auth
from tests import config
from tests import topics
from tests import test_utils


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
                    "kdc": {
                        "hostname": kerberos.get_host(),
                        "port": int(kerberos.get_port())
                    },
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
    sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                    "topic create {}".format(topic_name),
                    json=True)

    test_utils.wait_for_topic(kafka_server["package_name"], kafka_server["service"]["name"], topic_name)

    message = str(uuid.uuid4())

    log.info("Writing and reading: Writing to the topic, but not super user")
    assert not auth.write_to_topic("authorized", client_id, topic_name, message)

    log.info("Writing and reading: Writing to the topic, as super user")
    assert auth.write_to_topic("super", client_id, topic_name, message)

    log.info("Writing and reading: Reading from the topic, but not super user")
    assert auth.is_not_authorized(auth.read_from_topic("authorized", client_id, topic_name, 1))

    log.info("Writing and reading: Reading from the topic, as super user")
    assert message in auth.read_from_topic("super", client_id, topic_name, 1)

    zookeeper_endpoint = sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "endpoint zookeeper").strip()

    # TODO: If zookeeper has Kerberos enabled, then the environment should be changed
    topics.add_acls("authorized", client_id, topic_name, zookeeper_endpoint, env_str=None)

    # Send a second message which should not be authorized
    second_message = str(uuid.uuid4())
    log.info("Writing and reading: Writing to the topic, but not super user")
    assert auth.write_to_topic("authorized", client_id, topic_name, second_message)

    log.info("Writing and reading: Writing to the topic, as super user")
    assert auth.write_to_topic("super", client_id, topic_name, second_message)

    log.info("Writing and reading: Reading from the topic, but not super user")
    topic_output = auth.read_from_topic("authorized", client_id, topic_name, 3)
    assert message in topic_output
    assert second_message in topic_output

    log.info("Writing and reading: Reading from the topic, as super user")
    topic_output = auth.read_from_topic("super", client_id, topic_name, 3)
    assert message in topic_output
    assert second_message in topic_output

    # Check that the unauthorized client can still not read or write from the topic.
    log.info("Writing and reading: Writing to the topic, but not super user")
    assert not auth.write_to_topic("unauthorized", client_id, topic_name, second_message)

    log.info("Writing and reading: Reading from the topic, but not super user")
    assert auth.is_not_authorized(auth.read_from_topic("unauthorized", client_id, topic_name, 1))
