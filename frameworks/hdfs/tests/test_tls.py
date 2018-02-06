import pytest

import sdk_cmd
import sdk_install
import sdk_hosts
import sdk_plan
import sdk_security
import sdk_utils
import retrying
import shakedown
from tests import config


DEFAULT_JOURNAL_NODE_TLS_PORT = 8481
DEFAULT_NAME_NODE_TLS_PORT = 9003
DEFAULT_DATA_NODE_TLS_PORT = 9006


@pytest.fixture(scope='module')
def service_account(configure_security):
    """
    Creates service account and yields the name.
    """
    try:
        name = config.SERVICE_NAME
        sdk_security.create_service_account(
            service_account_name=name, service_account_secret=name)
        # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
        sdk_cmd.run_cli(
            "security org groups add_user superusers {name}".format(name=name))
        yield name
    finally:
        sdk_security.delete_service_account(
            service_account_name=name, service_account_secret=name)


@pytest.fixture(scope='module')
def hdfs_service_tls(service_account):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            service_name=config.SERVICE_NAME,
            expected_running_tasks=config.DEFAULT_TASK_COUNT,
            additional_options={
                "service": {
                    "service_account_secret": service_account,
                    "service_account": service_account,
                    "security": {
                        "transport_encryption": {
                            "enabled": True
                        }
                    }
                }
            },
            timeout_seconds=30 * 60)

        sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

        yield service_account
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
def test_healthy(hdfs_service_tls):
    config.check_healthy(service_name=config.SERVICE_NAME)


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.data_integrity
@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
def test_write_and_read_data_over_tls(hdfs_service_tls):
    test_filename = "test_data_tls"  # must be unique among tests in this suite
    config.write_data_to_hdfs(config.SERVICE_NAME, test_filename)
    config.read_data_from_hdfs(config.SERVICE_NAME, test_filename)


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.parametrize("node_type,port", [
    ('journal', DEFAULT_JOURNAL_NODE_TLS_PORT),
    ('name', DEFAULT_NAME_NODE_TLS_PORT),
    ('data', DEFAULT_DATA_NODE_TLS_PORT),
])
def test_verify_https_ports(node_type, port, hdfs_service_tls):
    """
    Verify that HTTPS port is open name, journal and data node types.
    """
    host = sdk_hosts.autoip_host(
        config.SERVICE_NAME, "{}-0-node".format(node_type), port)

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=config.DEFAULT_HDFS_TIMEOUT*1000,
        retry_on_result=lambda res: not res)
    def fn():
        exit_status, output = shakedown.run_command_on_master(
            _curl_https_get_code(host))
        return exit_status and output == '200'

    assert fn()


def _curl_https_get_code(host):
    """
    Create a curl command for a given host that outputs HTTP status code.
    """
    return (
        '/opt/mesosphere/bin/curl '
        '-s -o /dev/null -w "%{{http_code}}" '
        'https://{host}'
    ).format(host=host)
