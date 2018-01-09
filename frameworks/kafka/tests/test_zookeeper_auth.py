"""
This module tests the interaction of Kafka with Zookeeper with authentication enabled
"""
import logging
import uuid
import pytest

import shakedown

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_repository
import sdk_utils

from tests import auth
from tests import config
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

    principals.append("client@{realm}".format(realm=sdk_auth.REALM))

    yield principals


def get_node_principals():
    """Get a list of zookeeper principals for the agent nodes in the cluster"""
    principals = []

    agent_ips = shakedown.get_private_agents()
    agent_dashed_ips = list(map(
        lambda ip: "ip-{dashed_ip}".format(dashed_ip="-".join(ip.split("."))), agent_ips))
    for b in agent_dashed_ips:
        principals.append("zookeeper/{instance}.{domain}@{realm}".format(
            instance=b,
            # TODO(elezar) we need to infer the region too
            domain="us-west-2.compute.internal",
            realm=sdk_auth.REALM))

    return principals


@pytest.fixture(scope='module', autouse=True)
def zookeeper_principals():
    zk_fqdn = "{service_name}.{host_suffix}".format(service_name="kafka-zookeeper",
                                                    host_suffix=sdk_hosts.AUTOIP_HOST_SUFFIX)

    zk_ensemble = [
        "zookeeper-0-server",
        "zookeeper-1-server",
        "zookeeper-2-server",
    ]

    principals = []
    for b in zk_ensemble:
        principals.append("zookeeper/{instance}.{domain}@{realm}".format(
            instance=b,
            domain=zk_fqdn,
            realm=sdk_auth.REALM))

    principals.extend(get_node_principals())
    yield principals


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security, kafka_principals, zookeeper_principals):
    try:
        principals = []
        principals.extend(kafka_principals)
        principals.extend(zookeeper_principals)

        kerberos_env = sdk_auth.KerberosEnvironment()
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module')
def zookeeper_server(kerberos):
    service_kerberos_options = {
        "service": {
            "name": "kafka-zookeeper",
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc_host_name": kerberos.get_host(),
                    "kdc_host_port": int(kerberos.get_port()),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        }
    }

    try:
        sdk_install.uninstall("beta-kafka-zookeeper", "kafka-zookeeper")
        sdk_install.install(
            "beta-kafka-zookeeper",
            "kafka-zookeeper",
            6,
            additional_options=service_kerberos_options,
            timeout_seconds=30 * 60)

        yield {**service_kerberos_options, **{"package_name": "beta-kafka-zookeeper"}}

    finally:
        sdk_install.uninstall("beta-kafka-zookeeper", "kafka-zookeeper")


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
def test_client_can_read_and_write(kafka_client, kafka_server):
    client_id = kafka_client["id"]

    auth.wait_for_brokers(kafka_client["id"], kafka_client["brokers"])

    topic_name = "authn.test"
    sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                    "topic create {}".format(topic_name),
                    json=True)

    test_utils.wait_for_topic(kafka_server["package_name"], kafka_server["service"]["name"], topic_name)

    message = str(uuid.uuid4())

    assert auth.write_to_topic("client", client_id, topic_name, message)

    assert message in auth.read_from_topic("client", client_id, topic_name, 1)
