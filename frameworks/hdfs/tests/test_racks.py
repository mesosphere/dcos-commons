import pytest
import sdk_install
from tests import config


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        additional_options = {
            "name_node": {
                "placement": "[[\"@zone\", \"GROUP_BY\", \"1\"]]"
            }
        }
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=additional_options,
            timeout_seconds=30*60)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(autouse=True)
def pre_test_setup():
    config.check_healthy(service_name=config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.dcos_min_version('1.10')
def test_detect_racks():
    print_topology_cmd = "./bin/hdfs dfsadmin -printTopology"
    _, output = config.run_hdfs_command(config.SERVICE_NAME, print_topology_cmd)

    assert "Rack: /us-west" in output
