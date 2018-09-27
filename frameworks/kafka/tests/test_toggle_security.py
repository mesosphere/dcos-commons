import logging
import json
import tempfile
import pytest

import sdk_auth
import sdk_cmd
import sdk_install
import sdk_plan
import sdk_utils

from security import transport_encryption

from tests import auth
from tests import config
from tests import client


log = logging.getLogger(__name__)

TOPIC_NAME = "securetest"

pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"), reason="Security tests require DC/OS 1.10+"
    ),
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
def kerberos():
    """
    A pytest fixture that installs and configures a KDC used for testing.

    On teardown, the KDC application is removed.
    """
    try:
        kerberos_env = sdk_auth.KerberosEnvironment()

        principals = auth.get_service_principals(config.SERVICE_NAME, kerberos_env.get_realm())
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope="module", autouse=True)
def kafka_server(service_account):
    """
    A pytest fixture that installs a non-kerberized kafka service.

    On teardown, the service is uninstalled.
    """
    service_options = {
        "service": {
            "name": config.SERVICE_NAME,
            # Note that since we wish to toggle TLS which *REQUIRES* a service account,
            # we need to install Kafka with a service account to start with.
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
        )
        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope="module")
def kafka_client():
    try:
        kafka_client = client.KafkaClient("kafka-client", config.PACKAGE_NAME, config.SERVICE_NAME)
        kafka_client.install()

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.fixture(scope="module")
def kerberized_kafka_client(kerberos: sdk_auth.KerberosEnvironment):
    try:
        kafka_client = client.KafkaClient(
            "kerberized-kafka-client", config.PACKAGE_NAME, config.SERVICE_NAME, kerberos
        )
        kafka_client.install()

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.mark.incremental
def test_initial_kerberos_off_tls_off_plaintext_off(kafka_client: client.KafkaClient):
    assert kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kafka_client.check_users_can_read_and_write(["default"], TOPIC_NAME)


@pytest.mark.incremental
def test_forward_kerberos_on_tls_off_plaintext_off(
    kerberized_kafka_client: client.KafkaClient, kerberos: sdk_auth.KerberosEnvironment
):
    update_options = {
        "service": {
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        }
    }

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, update_options)
    assert kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kerberized_kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)


@pytest.mark.incremental
def test_forward_kerberos_on_tls_on_plaintext_on(kerberized_kafka_client: client.KafkaClient):
    update_options = {
        "service": {
            "security": {"transport_encryption": {"enabled": True, "allow_plaintext": True}}
        }
    }

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, update_options)

    kerberized_kafka_client._is_tls = True
    assert kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kerberized_kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)

    kerberized_kafka_client._is_tls = False
    assert kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kerberized_kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)


@pytest.mark.incremental
def test_forward_kerberos_on_tls_on_plaintext_off(kerberized_kafka_client: client.KafkaClient):
    update_options = {
        "service": {
            "security": {"transport_encryption": {"enabled": True, "allow_plaintext": False}}
        }
    }

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, update_options)

    kerberized_kafka_client._is_tls = False
    assert not kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kerberized_kafka_client._is_tls = True
    assert kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)

    kerberized_kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)


@pytest.mark.incremental
def test_forward_kerberos_off_tls_on_plaintext_off(kafka_client: client.KafkaClient):
    update_options = {"service": {"security": {"kerberos": {"enabled": False}}}}

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, update_options)
    kafka_client._is_tls = False
    assert not kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kafka_client._is_tls = True
    assert kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)


@pytest.mark.incremental
def test_forward_kerberos_off_tls_on_plaintext_on(kafka_client: client.KafkaClient):
    update_options = {
        "service": {
            "security": {"transport_encryption": {"enabled": True, "allow_plaintext": True}}
        }
    }

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, update_options)

    kafka_client._is_tls = False
    assert kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)
    kafka_client._is_tls = True
    assert kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)


@pytest.mark.incremental
def test_forward_kerberos_off_tls_off_plaintext_off(kafka_client: client.KafkaClient):
    update_options = {
        "service": {
            "security": {"transport_encryption": {"enabled": False, "allow_plaintext": False}}
        }
    }

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, update_options)

    kafka_client._is_tls = True
    assert not kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kafka_client._is_tls = False
    assert kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)


# We now run the tests in the oposite direction
@pytest.mark.incremental
def test_reverse_kerberos_off_tls_on_plaintext_on(kerberized_kafka_client: client.KafkaClient):
    test_forward_kerberos_on_tls_on_plaintext_on(kerberized_kafka_client)


@pytest.mark.incremental
def test_reverse_kerberos_off_tls_on_plaintext_off(kerberized_kafka_client: client.KafkaClient):
    test_forward_kerberos_on_tls_on_plaintext_off(kerberized_kafka_client)


@pytest.mark.incremental
def test_reverse_kerberos_on_tls_on_plaintext_off(
    kerberized_kafka_client: client.KafkaClient, kerberos: sdk_auth.KerberosEnvironment
):
    update_options = {
        "service": {
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        }
    }

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, update_options)
    kerberized_kafka_client._is_tls = False
    assert not kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    assert kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kerberized_kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)


@pytest.mark.incremental
def test_reverse_kerberos_on_tls_on_plaintext_on(kerberized_kafka_client: client.KafkaClient):
    update_options = {
        "service": {
            "security": {"transport_encryption": {"enabled": True, "allow_plaintext": True}}
        }
    }

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, update_options)

    kerberized_kafka_client._is_tls = False
    assert kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kerberized_kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)
    kerberized_kafka_client._is_tls = False
    assert kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kerberized_kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)


@pytest.mark.incremental
def test_reverse_kerberos_on_tls_off_plaintext_off(kerberized_kafka_client: client.KafkaClient):
    update_options = {
        "service": {
            "security": {"transport_encryption": {"enabled": False, "allow_plaintext": False}}
        }
    }

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, update_options)

    kerberized_kafka_client._is_tls = True
    assert not kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)

    kerberized_kafka_client._is_tls = False
    assert kerberized_kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kerberized_kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)


@pytest.mark.incremental
def test_reverse_kerberos_off_tls_off_plaintext_off(kafka_client: client.KafkaClient):
    update_options = {"service": {"security": {"kerberos": {"enabled": False}}}}

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, update_options)

    kafka_client._is_tls = True
    assert not kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kafka_client._is_tls = False
    assert kafka_client.connect(config.DEFAULT_BROKER_COUNT)
    kafka_client.check_users_can_read_and_write(["client"], TOPIC_NAME)


def update_service(package_name: str, service_name: str, options: dict):
    with tempfile.NamedTemporaryFile("w", suffix=".json") as f:
        options_path = f.name

        log.info("Writing updated options to %s", options_path)
        json.dump(options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(options_path)]
        sdk_cmd.svc_cli(package_name, service_name, " ".join(cmd))

        # An update plan is a deploy plan
        sdk_plan.wait_for_kicked_off_deployment(service_name)
        sdk_plan.wait_for_completed_deployment(service_name)
