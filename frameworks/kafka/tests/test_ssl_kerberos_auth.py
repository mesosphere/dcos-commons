import logging
import pytest

import sdk_auth
import sdk_cmd
import sdk_install
import sdk_utils

from security import transport_encryption

from tests import auth
from tests import client
from tests import config


log = logging.getLogger(__name__)


pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"), reason="TLS tests require DC/OS 1.10+")
]


@pytest.fixture(scope="module", autouse=True)
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        name = config.SERVICE_NAME
        service_account_info = transport_encryption.setup_service_account(name)

        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(config.SERVICE_NAME, service_account_info)


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
def kafka_server(kerberos, service_account):
    """
    A pytest fixture that installs a Kerberized kafka service.

    On teardown, the service is uninstalled.
    """
    service_kerberos_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                },
                "transport_encryption": {"enabled": True},
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

        yield {**service_kerberos_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope="module", autouse=True)
def kafka_client(kerberos):
    try:
        kafka_client = client.KafkaClient("kafka-client")
        kafka_client.install(kerberos)

        # TODO: This flag should be set correctly.
        kafka_client._is_tls = True

        transport_encryption.create_tls_artifacts(cn="client", marathon_task=kafka_client.get_id())

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.mark.sanity
def test_client_can_read_and_write(kafka_client: client.KafkaClient, kafka_server, kerberos):

    topic_name = "tls.topic"
    sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "topic create {}".format(topic_name),
        json=True,
    )

    kafka_client.connect(kafka_server)

    user = "client"
    write_success, read_successes, _ = kafka_client.can_write_and_read(
        user, kafka_server, topic_name, kerberos
    )

    assert write_success, "Write failed (user={})".format(user)
    assert read_successes, (
        "Read failed (user={}): "
        "MESSAGES={} "
        "read_successes={}".format(user, kafka_client.MESSAGES, read_successes)
    )
