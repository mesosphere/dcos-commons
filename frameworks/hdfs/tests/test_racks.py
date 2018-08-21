import pytest
import sdk_fault_domain
import sdk_install
import sdk_marathon
import sdk_utils
from tests import config


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        additional_options = {"name_node": {"placement": '[["@zone", "GROUP_BY", "1"]]'}}
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=additional_options,
            timeout_seconds=30 * 60,
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(autouse=True)
def pre_test_setup():
    config.check_healthy(service_name=config.SERVICE_NAME)


@pytest.fixture(scope="module", autouse=True)
def hdfs_client():
    try:
        client = config.get_hdfs_client_app(config.SERVICE_NAME)
        sdk_marathon.install_app(client)
        yield client

    finally:
        sdk_marathon.destroy_app(client["id"])


@pytest.mark.sanity
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version("1.11")
def test_detect_racks():
    success, stdout, stderr = config.run_client_command(config.hadoop_command("dfsadmin -printTopology"))
    assert success

    # expecting e.g. "Rack: /aws/us-west-2b\n..."
    rack = stdout.split("\n")[0]
    assert rack.startswith("Rack: /")
    assert sdk_fault_domain.is_valid_zone(rack[len("Rack: /") :])
