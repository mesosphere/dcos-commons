import logging
import pytest
import tempfile


import sdk_cmd
import sdk_install
import sdk_plan
import sdk_utils

from tests import config
from tests import nodetool


log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.11')
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_rack():
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        3,
        additional_options={
            "service": {
                "name": config.SERVICE_NAME
            },
            "nodes": {
                "placement_constraint": "[[\"@zone\", \"GROUP_BY\", \"1\"]]"
            }
        })

    raw_status = nodetool.cmd(config.SERVICE_NAME, 'node-0-server', 'status')
    log.info("raw_status: {}".format(raw_status))
    stdout = raw_status[1]
    log.info("stdout: {}".format(stdout))

    node = nodetool.parse_status(stdout)[0]
    log.info("node: {}".format(node))

    assert node.get_rack() != 'rack1'
    assert 'us-west' in node.get_rack()


@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_rack_upgrades_to_default_rack():
    # First uninstall the existing package
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    # Install the last verison with the `service.rack` setting
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        3,
        additional_options={
            "service": {
                "rack": "not-rack1"
            }
        },
        package_version="2.0.3-3.0.14"
    )

    target_version = "2.1.0-3.0.16"
    # Run the CLI upgrade
    cmd_list = [
        "package", "install", "--yes", "--cli",
        "--package-version={}".format(target_version)
    ]
    sdk_cmd.run_cli(" ".join(cmd_list))

    # Update the package in-place
    cmd_list = [
        "update", "start",
        "--package-version={}".format(target_version)
    ]
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, " ".join(cmd_list))

    # An update plan is a deploy plan
    sdk_plan.wait_for_kicked_off_deployment(config.SERVICE_NAME)
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.11')
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_adding_zone_placement_constraint_fails_racks():
    # First uninstall the existing package
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        3,
        additional_options={})

    new_options = {
        "nodes": {
            "placement_constraint": "[[\"@zone\", \"GROUP_BY\", \"1\"]]"
        }
    }

    update_service(config.PACKAGE_NAME, config.SERVICE_NAME, new_options)


def update_service(package_name: str, service_name: str, options: dict):
    # TODO: This should be refactored to a common place.
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
