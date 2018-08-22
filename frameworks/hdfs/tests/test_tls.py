import logging
import pytest
import retrying


import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_recovery
import sdk_utils


from security import transport_encryption

from tests import config

pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"), reason="TLS tests require DC/OS 1.10+"
    ),
]


LOG = logging.getLogger(__name__)


DEFAULT_JOURNAL_NODE_TLS_PORT = 8481
DEFAULT_NAME_NODE_TLS_PORT = 9003
DEFAULT_DATA_NODE_TLS_PORT = 9006


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
def hdfs_service(service_account):
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

        yield {**service_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope="module", autouse=True)
def hdfs_client(hdfs_service):
    try:
        client = config.get_hdfs_client_app(hdfs_service["service"]["name"])
        sdk_marathon.install_app(client)
        yield client

    finally:
        sdk_marathon.destroy_app(client["id"])


@pytest.mark.tls
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_healthy(hdfs_service):
    config.check_healthy(service_name=config.SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.data_integrity
@sdk_utils.dcos_ee_only
def test_write_and_read_data_over_tls(hdfs_service, hdfs_client):
    test_filename = config.get_unique_filename("test_data_tls")
    config.hdfs_client_write_data(test_filename)
    config.hdfs_client_read_data(test_filename)


@pytest.mark.tls
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
@pytest.mark.parametrize(
    "node_type,port",
    [
        ("journal", DEFAULT_JOURNAL_NODE_TLS_PORT),
        ("name", DEFAULT_NAME_NODE_TLS_PORT),
        ("data", DEFAULT_DATA_NODE_TLS_PORT),
    ],
)
def test_verify_https_ports(node_type, port, hdfs_service):
    """
    Verify that HTTPS port is open name, journal and data node types.
    """
    host = sdk_hosts.autoip_host(config.SERVICE_NAME, "{}-0-node".format(node_type), port)

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=config.DEFAULT_HDFS_TIMEOUT * 1000,
        retry_on_result=lambda res: not res,
    )
    def fn():
        rc, stdout, _ = sdk_cmd.master_ssh(_curl_https_get_code(host))
        return rc == 0 and stdout == "200"

    assert fn()


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.recovery
def test_tls_recovery(hdfs_service, service_account):
    pod_list = [
        "name-0",
        "name-1",
        "data-0",
        "data-1",
        "data-2",
        "journal-0",
        "journal-1",
        "journal-2",
    ]
    for pod in pod_list:
        sdk_recovery.check_permanent_recovery(
            hdfs_service["package_name"],
            hdfs_service["service"]["name"],
            pod,
            recovery_timeout_s=25 * 60,
        )


def _curl_https_get_code(host):
    """
    Create a curl command for a given host that outputs HTTP status code.
    """
    return (
        "/opt/mesosphere/bin/curl " '-s -o /dev/null -w "%{{http_code}}" ' "https://{host}"
    ).format(host=host)
