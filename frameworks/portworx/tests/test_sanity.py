import os
import pytest
import sdk_install
import sdk_networks
import sdk_cmd
import sdk_upgrade
import shakedown
import logging
from tests import config
from tests import px_utils
from time import sleep

log = logging.getLogger(__name__)

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
    px_status = px_utils.check_px_status() 
   
    log.info("Portworx service status is: {}".format(px_status))
    if 2 != px_status:
        log.info("PORTWORX: service status returned: {}".format(px_status))
        raise

# Test the restart functionality of one pod
@pytest.mark.sanity
def test_restart_px_pod():
    px_pod_list = px_utils.get_px_pod_list()
    num_of_pods = len(px_pod_list)
    if 0 >= num_of_pods:
        log.info("PORTWORX: Pod list count is: {} ".format(len(px_pod_list)) )
        raise
    
    px_utils.restart_pod(px_pod_list[0])
    sleep(5) # Is that enough time ?

    px_pod_list = px_utils.get_px_pod_list()
    if num_of_pods != len(px_pod_list):
        log.info("PORTWORX: Pod count is not equal after restarting one pod")
        raise

# Test the force restart functionality of deploy plan
@pytest.mark.sanity
def test_force_restart_plan():
    plan_name = "deploy"
    px_utils.force_restart_plan(plan_name)
    plan_status = px_utils.check_plan_status(plan_name)
    if plan_status == "COMPLETE":
        log.info("PORTWORX: The {} plan has not restarted, plan status is: {}".format(plan_name, plan_status))
        raise
    plan_status = px_utils.wait_for_plan_complete(plan_name)
    if plan_status != "COMPLETE":
        log.info("PORTWORX: Failed to restart plan {}, plan status is: {}".format(plan_name, plan_status))
        raise

# Test stop deploy plan functionality
@pytest.mark.sanity
def test_stop_plan():
    plan_name = "deploy"
    px_utils.stop_plan(plan_name)
    plan_status = px_utils.check_plan_status(plan_name)
    if plan_status != "WAITING":
        log.info("PORTWORX: The {} plan failed to stop, plan status is: {}".format(plan_name, plan_status))
        raise

# Test start deploy plan functionality
@pytest.mark.sanity
def test_start_plan():
    plan_name = "deploy"
    px_utils.start_plan(plan_name)
    plan_status = px_utils.wait_for_plan_complete(plan_name)
    if plan_status != "COMPLETE":
        log.info("PORTWORX: Failed to start plan {}, plan status is: {}".format(plan_name, plan_status))
        raise

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
        info.log("PORTWORX: Px specific cleanup failed.")
        raise
