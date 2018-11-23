import json
import logging
import pytest
from toolz import get_in

import sdk_cmd
import sdk_install
import sdk_networks
import sdk_recovery
import sdk_security
import sdk_utils

from security import transport_encryption, cipher_suites

from tests import config, client

pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"), reason="TLS tests require DC/OS 1.10+"
    ),
]

log = logging.getLogger(__name__)

# Name of the broker TLS vip
BROKER_TLS_ENDPOINT = "broker-tls"
TLS_USER = "client"


@pytest.fixture(scope="module")
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


@pytest.fixture(scope="module")
def kafka_client():
    try:
        kafka_client = client.KafkaClient("kafka-client", config.PACKAGE_NAME, config.SERVICE_NAME)
        kafka_client.install()

        # TODO: This flag should be set correctly.
        kafka_client._is_tls = True
        transport_encryption.create_tls_artifacts(TLS_USER, "kafka-client")

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.fixture(scope="module", autouse=True)
def kafka_server(service_account, kafka_client: client.KafkaClient):
    service_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {"transport_encryption": {"enabled": True}},
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            service_name=config.SERVICE_NAME,
            expected_running_tasks=config.DEFAULT_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
        )
        kafka_client.connect(config.DEFAULT_BROKER_COUNT)
        kafka_client.create_topic(config.DEFAULT_TOPIC_NAME)
        kafka_client.check_topic_partition_count(
            config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT
        )

        yield {**service_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.smoke
@pytest.mark.sanity
def test_tls_endpoints():
    endpoint_names = sdk_networks.get_endpoint_names(config.PACKAGE_NAME, config.SERVICE_NAME)
    assert len(endpoint_names) == 2
    assert BROKER_TLS_ENDPOINT in endpoint_names

    # Test that broker-tls endpoint is available
    endpoint_tls = sdk_networks.get_endpoint(
        config.PACKAGE_NAME, config.SERVICE_NAME, BROKER_TLS_ENDPOINT
    )
    assert len(endpoint_tls["dns"]) == config.DEFAULT_BROKER_COUNT


@pytest.mark.tls
@pytest.mark.sanity
def test_topic_write_and_read_over_tls(kafka_client: client.KafkaClient):
    kafka_client.check_users_can_read_and_write([TLS_USER], config.DEFAULT_TOPIC_NAME)


@pytest.mark.tls
@pytest.mark.smoke
@pytest.mark.sanity
def test_producer_over_tls(kafka_client: client.KafkaClient):
    # Write twice: Warm up TLS connections
    num_messages = 10
    sdk_cmd.svc_cli(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        "topic producer_test_tls {} {}".format(config.DEFAULT_TOPIC_NAME, num_messages),
    )

    rc, stdout, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        "topic producer_test_tls {} {}".format(config.DEFAULT_TOPIC_NAME, num_messages),
    )
    assert rc == 0, "producer_test_tls failed"
    write_info = json.loads(stdout)
    assert len(write_info) == 1
    assert write_info["message"].startswith("Output: {} records sent".format(num_messages))


@pytest.mark.tls
@pytest.mark.smoke
@pytest.mark.sanity
def test_tls_ciphers():
    task_name = "kafka-0-broker"
    endpoint = sdk_networks.get_endpoint(
        config.PACKAGE_NAME, config.SERVICE_NAME, BROKER_TLS_ENDPOINT
    )["dns"][0]
    ciphers_config_path = ["service", "security", "transport_encryption", "ciphers"]
    rc, stdout, _ = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "describe")
    assert rc == 0, "Describe command failed"
    expected_ciphers = set(get_in(ciphers_config_path, json.loads(stdout), "").rstrip().split(","))

    openssl_ciphers = sdk_security.openssl_ciphers()
    missing_openssl_ciphers = cipher_suites.missing_openssl_ciphers(openssl_ciphers)
    possible_openssl_ciphers = openssl_ciphers - missing_openssl_ciphers
    enabled_ciphers = set()

    assert openssl_ciphers, "OpenSSL ciphers should be non-empty"
    assert expected_ciphers, "Expected ciphers should be non-empty"
    assert possible_openssl_ciphers, "Possible OpenSSL ciphers should be non-empty"

    # Output OpenSSL version.
    sdk_cmd.service_task_exec(config.SERVICE_NAME, task_name, "openssl version")
    log.warning(
        "\n%s OpenSSL ciphers missing from the cipher_suites module:", len(missing_openssl_ciphers)
    )
    log.warning("\n".join(to_sorted(list(missing_openssl_ciphers))))
    log.info("\n%s expected ciphers:", len(expected_ciphers))
    log.info("\n".join(to_sorted(list(expected_ciphers))))
    log.info("\n%s ciphers will be checked:", len(possible_openssl_ciphers))
    for openssl_cipher in to_sorted(list(possible_openssl_ciphers)):
        log.info("%s (%s)", cipher_suites.rfc_name(openssl_cipher), openssl_cipher)

    for openssl_cipher in possible_openssl_ciphers:
        if sdk_security.is_cipher_enabled(config.SERVICE_NAME, task_name, openssl_cipher, endpoint):
            enabled_ciphers.add(cipher_suites.rfc_name(openssl_cipher))

    log.info("%s ciphers enabled out of %s:", len(enabled_ciphers), len(possible_openssl_ciphers))
    log.info("\n".join(to_sorted(list(enabled_ciphers))))

    assert expected_ciphers == enabled_ciphers, "Enabled ciphers should match expected ciphers"


def to_sorted(coll):
    """ Sorts a collection and returns it. """
    coll.sort()
    return coll


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.recovery
def test_tls_recovery():
    rc, stdout, _ = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "pod list")
    assert rc == 0, "Pod list failed"

    for pod in json.loads(stdout):
        sdk_recovery.check_permanent_recovery(
            config.PACKAGE_NAME, config.SERVICE_NAME, pod, recovery_timeout_s=25 * 60
        )
