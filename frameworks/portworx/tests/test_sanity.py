import os
import pytest
import sdk_install
import sdk_networks
import sdk_cmd
import sdk_upgrade
import shakedown
from tests import config

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.portworx_cleanup()
        # The sdk_install installs portworx framework and CLI commands for portworx
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=sdk_install.merge_dictionaries(sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS, config.PX_NODE_OPTIONS))

        yield
    finally:
        return

# Verify portworx installation
@pytest.mark.sanity
def test_verify_install():
    shakedown.service_healthy(config.SERVICE_NAME)

# Upgrade portworx framework from released version
@pytest.mark.sanity
def test_upgrade_framework():
    sdk_upgrade.test_upgrade(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT,
        additional_options=sdk_install.merge_dictionaries(sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS, config.PX_NODE_OPTIONS))

# Uninstall portworx
@pytest.mark.sanity
def test_uninstall_package():
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

# Do post uninstall cleanup
@pytest.mark.sanity
def test_post_uninstall_cleanup():
    if sdk_install.portworx_cleanup() != 0:
        info.log("Portworx specific cleanup failed.")
        raise
