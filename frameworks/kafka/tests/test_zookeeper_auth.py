"""
This module tests the interaction of Kafka with Zookeeper with authentication enabled
"""
import logging
import pytest

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_security
import sdk_utils

from security import kerberos as krb5

from tests import auth
from tests import client
from tests import config


pytestmark = [sdk_utils.dcos_ee_only,
              pytest.mark.skipif(sdk_utils.dcos_version_less_than("1.10"),
                                 reason="Kerberos tests require DC/OS 1.10 or higher")]


log = logging.getLogger(__name__)


def get_zookeeper_principals(service_name: str, realm: str) -> list:
    primaries = ["zookeeper", ]

    tasks = [
        "zookeeper-0-server",
        "zookeeper-1-server",
        "zookeeper-2-server",
    ]
    instances = map(lambda task: sdk_hosts.autoip_host(service_name, task), tasks)

    principals = krb5.generate_principal_list(primaries, instances, realm)
    return principals


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security):
    try:
        kerberos_env = sdk_auth.KerberosEnvironment()

        principals = auth.get_service_principals(config.SERVICE_NAME,
                                                 kerberos_env.get_realm())
        principals.extend(get_zookeeper_principals(config.ZOOKEEPER_SERVICE_NAME,
                                                   kerberos_env.get_realm()))

        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module')
def zookeeper_server(kerberos):
    service_options = {
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

    zk_account = "kafka-zookeeper-service-account"
    zk_secret = "kakfa-zookeeper-secret"

    if sdk_utils.is_strict_mode():
        service_options = sdk_utils.merge_dictionaries({
            'service': {
                'service_account': zk_account,
                'service_account_secret': zk_secret,
            }
        }, service_options)

    try:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        service_account_info = sdk_security.setup_security(config.ZOOKEEPER_SERVICE_NAME,
                                                           linux_user="nobody",
                                                           service_account=zk_account,
                                                           service_account_secret=zk_secret)
        sdk_install.install(
            config.ZOOKEEPER_PACKAGE_NAME,
            config.ZOOKEEPER_SERVICE_NAME,
            config.ZOOKEEPER_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
            insert_strict_options=False)

        yield {**service_options, **{"package_name": config.ZOOKEEPER_PACKAGE_NAME}}

    finally:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        sdk_security.cleanup_security(config.ZOOKEEPER_SERVICE_NAME, service_account_info)


@pytest.fixture(scope='module', autouse=True)
def kafka_server(kerberos, zookeeper_server):

    # Get the zookeeper DNS values
    zookeeper_dns = sdk_cmd.svc_cli(zookeeper_server["package_name"],
                                    zookeeper_server["service"]["name"],
                                    "endpoint clientport", json=True)["dns"]

    service_options = {
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
            additional_options=service_options,
            timeout_seconds=30 * 60)

        yield {**service_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def kafka_client(kerberos):
    try:
        kafka_client = client.KafkaClient("kafka-client")
        kafka_client.install(kerberos)

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.zookeeper
@pytest.mark.sanity
def test_client_can_read_and_write(kafka_client: client.KafkaClient, kafka_server, kerberos):

    topic_name = "authn.test"
    sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                    "topic create {}".format(topic_name),
                    json=True)

    kafka_client.connect(kafka_server)

    user = "client"
    write_success, read_successes, _ = kafka_client.can_write_and_read(user, kafka_server, topic_name, kerberos)
    assert write_success, "Write failed (user={})".format(user)
    assert read_successes, "Read failed (user={}): " \
                           "MESSAGES={} " \
                           "read_successes={}".format(user,
                                                      kafka_client.MESSAGES,
                                                      read_successes)
