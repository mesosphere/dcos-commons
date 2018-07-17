import logging
import pytest
import sdk_install
import sdk_upgrade
import sdk_utils

from tests import config
from tests import nodetool

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())


@pytest.mark.dcos_min_version("1.11")
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_rack():
    sdk_install.install(
        config.PACKAGE_NAME,
        config.get_foldered_service_name(),
        3,
        additional_options={
            "service": {"name": config.get_foldered_service_name()},
            "nodes": {"placement_constraint": '[["@zone", "GROUP_BY", "1"]]'},
        },
    )

    raw_status = nodetool.cmd(
        config.get_foldered_service_name(), "node-0-server", "status"
    )
    log.info("raw_status: {}".format(raw_status))
    stdout = raw_status[1]
    log.info("stdout: {}".format(stdout))

    node = nodetool.parse_status(stdout)[0]
    log.info("node: {}".format(node))

    assert node.get_rack() != "rack1"
    assert "us-west" in node.get_rack()


@pytest.mark.sanity
def test_custom_rack_upgrade():
    service_options = {"service": {"rack": "not-rack1"}}
    sdk_upgrade.test_upgrade(
        config.PACKAGE_NAME,
        config.get_foldered_service_name(),
        config.DEFAULT_TASK_COUNT,
        additional_options=service_options,
    )
