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

# Verify pod list is as per deployment
@pytest.mark.sanity
def test_verify_pod_list():
    num_of_pods, px_pod_list = px_utils.get_px_pod_list()
    if 1 != num_of_pods:
        log.info("PORTWORX: Pod list count is: {}, expected 1".format(num_of_pods))
        raise

# Verify px service configuration as per deployment
@pytest.mark.sanity
def test_verify_config_params():
    px_options, px_kvdb, px_image = px_utils.get_px_options_kvdb_image()

    if px_options.strip('\"') != config.PX_OPTIONS:
        log.info("PORTWORX: Px options are not as per provided configuation, provided: {}, obtained: {}".format(config.PX_OPTIONS, px_options))
        raise

    if px_kvdb.strip('\"') != config.PX_KVDB_SERVER:
        log.info("PORTWORX: Px kvdb server not as per provided configuation, provided: {}, obtained: {}".format(config.PX_KVDB_SERVER, px_kvdb))
        raise

    if px_image.strip('\"') != config.PX_IMAGE:
        log.info("PORTWORX: Px Image name is not as per provided configuation, provided: {}, obtained: {}".format(config.PX_IMAGE, px_image))
        raise

# Replace portworx pod and move it to new agent
@pytest.mark.sanity
def test_replace_and_move_pod():
    pod_count, pod_list = px_utils.get_px_pod_list()
    if pod_count <= 0:
        log.info("PORTWORX: Pod count is: {}".format(pod_count))
        raise

    pod_name = pod_list[1]
    log.info("PORTWORX: Pod name to be replaced and move: {}".format(pod_name))

    pod_agent_id_old, pod_agent_id_new = px_utils.replace_pod(pod_name)
    if pod_agent_id_old == pod_agent_id_new:
        log.info("PORTWORX: Failed to replace and move pod. Old pod agent id: {} , New pod agent id: {}".format(pod_agent_id_old, pod_agent_id_new))
        raise
# Verify suspend and resume portworx service
@pytest.mark.sanity
def test_suspend_resume_px_service():
    pod_count, pod_list = px_utils.get_px_pod_list()
    if pod_count <= 0:
        log.info("PORTWORX: Pod count is: {}".format(pod_count))
        raise

    pod_name = pod_list[1]
    px_utils.px_service_suspend_resume(pod_name)

    px_status = px_utils.check_px_status() 
    if 2 != px_status:
        log.info("PORTWORX: Failed service stop start px status returned: {}".format(px_status))
        raise

# Test the restart functionality of one pod
@pytest.mark.sanity
def test_restart_px_pod():
    num_of_pods, px_pod_list = px_utils.get_px_pod_list()
    if 0 >= num_of_pods:
        log.info("PORTWORX: Pod list count is: {} ".format(num_of_pods))
        raise
    
    px_utils.restart_pod(px_pod_list[0])
    sleep(5) # Is that enough time ?

    num_pods_after, px_pod_list = px_utils.get_px_pod_list()
    if num_of_pods != num_pods_after:
        log.info("PORTWORX: Pod count is not equal after restarting one pod, before: {}, after: {}".format(num_of_pods, num_pods_after))
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
