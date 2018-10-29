import logging
import pytest

import sdk_auth
import sdk_install
import sdk_networks
import sdk_utils

from tests import auth
from tests import client
from tests import config


log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def kerberos(configure_security):
    try:
        kerberos_env = sdk_auth.KerberosEnvironment()

        principals = auth.get_service_principals(config.SERVICE_NAME, kerberos_env.get_realm())
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope="module", autouse=True)
def kafka_server(kerberos, kafka_client: client.KafkaClient):
    """
    A pytest fixture that installs a Kerberized kafka service.

    On teardown, the service is uninstalled.
    """
    service_kerberos_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            },
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_kerberos_options,
            timeout_seconds=30 * 60,
        )

        kafka_client.connect(config.DEFAULT_BROKER_COUNT)
        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope="module")
def kafka_client(kerberos):
    try:
        kafka_client = client.KafkaClient(
            "kafka-client", config.PACKAGE_NAME, config.SERVICE_NAME, kerberos
        )
        kafka_client.install()

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_no_vip(kafka_server):
    broker_endpoint = sdk_networks.get_endpoint(config.PACKAGE_NAME, config.SERVICE_NAME, "broker")
    assert "vip" not in broker_endpoint


@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_client_can_read_and_write(kafka_client: client.KafkaClient):
    topic_name = "authn.test"
    kafka_client.create_topic(topic_name)
    kafka_client.check_users_can_read_and_write(["client"], topic_name)
