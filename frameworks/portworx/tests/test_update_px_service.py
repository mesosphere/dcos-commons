# Here are tests for various update options available for portworx.
# For example updating portworx cluster from 1 node to 3 node, updating portworx with enabling etcd etc.

import json
import logging
import pytest
import shakedown
import tempfile

import sdk_cmd
import sdk_install
import sdk_jobs
import sdk_plan
import sdk_security
import sdk_utils
import os

from security import transport_encryption

from tests import config
from tests import px_utils
from tests import dcos_utils
from time import sleep

log = logging.getLogger(__name__)


@pytest.fixture(scope='module')
def service_account(configure_security):
    """
    Creates service account and secret and yields dict containing both.
    """
    try:
        name = config.SERVICE_NAME
        secret = "{}-secret".format(name)
        sdk_security.create_service_account(
            service_account_name=name, service_account_secret=secret)
        # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
        sdk_cmd.run_cli(
            "security org groups add_user superusers {name}".format(name=name))
        yield {"name": name, "secret": secret}
    finally:
        sdk_security.delete_service_account(
            service_account_name=name, service_account_secret=secret)


@pytest.fixture(scope='module')
def dcos_ca_bundle():
    """
    Retrieve DC/OS CA bundle and returns the content.
    """
    resp = sdk_cmd.cluster_request('GET', '/ca/dcos-ca.crt')
    cert = resp.content.decode('ascii')
    assert cert is not None
    return cert


@pytest.fixture(scope='module', autouse=True)
def portworx_service(service_account):
    """
    A pytest fixture that installs the portworx service.
    On teardown, the service is uninstalled.
    """
    
    options = {
        "service": {
            "name": config.SERVICE_NAME,
            "virtual_network_enabled": True,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    px_cluster_name = "portworx-dcos-" + config.get_random_string(12)
    config.PX_NODE_OPTIONS["node"]["portworx_cluster"] = px_cluster_name
    config.PX_NODE_OPTIONS["node"]["portworx_image"] = os.environ['PX_OLD_IMAGE']
    # Lets use internal kvdb for update tests. So keep kvdb server empty
    config.PX_NODE_OPTIONS["node"]["kvdb_servers"] = ""
    config.PX_NODE_OPTIONS["node"]["internal_kvdb"] = True

    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=sdk_install.merge_dictionaries(options, config.PX_NODE_OPTIONS),
            wait_for_deployment=True)

        # Wait for service health check to pass
        shakedown.service_healthy(config.SERVICE_NAME)
        px_status = px_utils.check_px_status()
        if 2 != px_status:
            log.info("PORTWORX: Px service status is: {}".format(px_status))

        yield {**options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        return

# Test create storage policy, set it default create volume and verify
@pytest.mark.sanity
def test_create_set_storage_policy():
    pod_count, pod_list = px_utils.get_px_pod_list()
    if pod_count <= 0:
        log.info("PORTWORX: Can't proceed with storage policy creation, Pod count is: {}".format(pod_count))
        raise
    pod_name = pod_list[1]
    px_utils.px_storage_policy_create(pod_name)
    px_utils.px_storage_policy_set_default(pod_name)
    px_utils.px_create_volume(pod_name, "stp_volume1")
    assert px_utils.px_is_vol_encrypted(config.PX_SEC_OPTIONS["stp_volume1"]),
            "PORTWORX: Failed to create repl=2 volume as per storage policy before px_image update"

@pytest.mark.pxinstall
@pytest.mark.authinstall
@pytest.mark.install
@pytest.mark.sanity
def test_update_px_image():
    portworx_service()
    px_image = os.environ['PX_IMAGE']
    update_options = {
        "node": {
            "portworx_image": px_image
            }
        }

    update_service(update_options, False)
    px_status = px_utils.check_px_status() 
    assert px_status == 2, "PORTWORX: Update Px image failed px service status: {}".format(px_status)

# Verify that storage policy set to default before px image update should work after px image update as well
@pytest.mark.sanity
def test_verify_default_storage_policy_after_pximage_update():
    pod_count, pod_list = px_utils.get_px_pod_list()
    if pod_count <= 0:
        log.info("PORTWORX: Can't proceed with storage policy creation, Pod count is: {}".format(pod_count))
        raise
    pod_name = pod_list[1]
    px_utils.px_create_volume(pod_name, "stp_volume2")
    assert px_utils.px_is_vol_encrypted(config.PX_SEC_OPTIONS["stp_volume2"]),
            "PORTWORX: Failed to create repl=2 volume as per storage policy after px-image update"

@pytest.mark.authinstall
@pytest.mark.sanity
def test_enable_auth():
    portworx_service()
    update_options = {
        "node": {
            "portworx_options": config.PX_NODE_OPTIONS["node"]["portworx_options"] + config.PX_AUTH_OPTIONS
            }
        }
    update_service(update_options, False)
    px_status = px_utils.check_px_status() 
    assert px_status == 2, "PORTWORX: Enable Px security (auth) failed. px service status: {}".format(px_status)


# Test update portworx framework with secrets enabled.
@pytest.mark.sanity
def test_enable_secrets_and_verify():
    dcos_utils.install_enterprise_cli()
    if not dcos_utils.check_secret_present(config.SERVICE_NAME, config.PX_SEC_OPTIONS["user_secret_id"]):
        dcos_utils.create_dcos_user(config.PX_SEC_OPTIONS["user_name"], config.PX_SEC_OPTIONS["user_password"])
        dcos_utils.create_dcos_secrets(config.PX_SEC_OPTIONS["secret_value"], config.PX_SEC_OPTIONS["base_path"], config.PX_SEC_OPTIONS["secret_key"])
        dcos_utils.create_dcos_secrets(config.PX_SEC_OPTIONS["user_name"], config.SERVICE_NAME, config.PX_SEC_OPTIONS["user_secret_id"])
        dcos_utils.create_dcos_secrets(config.PX_SEC_OPTIONS["user_password"], config.SERVICE_NAME, config.PX_SEC_OPTIONS["password_secret_id"])
        dcos_utils.grant_permissions_to_user(config.PX_SEC_OPTIONS["user_name"], config.PX_SEC_OPTIONS["base_path"])
 
   
    assert dcos_utils.check_secret_present(config.SERVICE_NAME, config.PX_SEC_OPTIONS["user_secret_id"]), " User secrets is not present in the list"
    sleep(60)

    # Update portworx service with enabled secrets
    update_options = {
        "secrets": {
            "enabled": True,
            "base_path": config.PX_SEC_OPTIONS["base_path"],
            "dcos_username_secret": config.SERVICE_NAME + "/" + config.PX_SEC_OPTIONS["user_secret_id"],
            "dcos_password_secret": config.SERVICE_NAME + "/" + config.PX_SEC_OPTIONS["password_secret_id"]
            }
        }

    update_service(update_options)
    sleep(60)
 
    # Restart portworx service on all px nodes
    pod_count, pod_list = px_utils.get_px_pod_list()
    if pod_count <= 0:
        log.info("PORTWORX: Can't proceed with restart px service at test_enable_secrets_and_verify, Pod count is: {}".format(pod_count))
        raise
    px_utils.px_restart_portworx_service(pod_list[1])
    sleep(60)

# Test create encrypted px volume
@pytest.mark.sanity
def test_create_encrypted_px_volume():
    pod_count, pod_list = px_utils.get_px_pod_list()
    if pod_count <= 0:
        log.info("PORTWORX: Can't proceed with volume creation, Pod count is: {}".format(pod_count))
        raise
    pod_name = pod_list[1]
    px_utils.px_create_encrypted_volume(pod_name, config.PX_SEC_OPTIONS["encrypted_volume_name"], config.PX_SEC_OPTIONS["secret_key"])
    assert px_utils.px_is_vol_encrypted(config.PX_SEC_OPTIONS["encrypted_volume_name"]), "PORTWORX: Failed to create encrypted volume."

@pytest.mark.pxinstall
@pytest.mark.sanity
def test_update_node_count():
    portworx_service()
    update_options = {
        "node": {
            "count": config.PX_NUM_NODES
            }
        }

    update_service(update_options)
    px_status = px_utils.check_px_status() 
    assert px_status == 2, "PORTWORX: Update node count failed px service status: {}".format(px_status)

    px_node_count = px_utils.get_px_node_count()
    if config.PX_NUM_NODES != px_node_count:
        log.info("PORTWORX: Failed to update node count  to 3, node count is: {}".format(px_node_count))
        raise

@pytest.mark.sanity
def test_update_enable_lighthouse():
    portworx_service()
    update_options = {
        "lighthouse": {
            "enabled": True
            }
        }

    update_service(update_options)

@pytest.mark.sanity
def test_update_disable_lighthouse():
    portworx_service()
    update_options = {
        "lighthouse": {
            "enabled": False
            }
        }

    update_service(update_options, False)

def test_update_enable_etcd():
    portworx_service()
    update_options = {
        "etcd": {
            "enabled": True
            }
        }

    update_service(update_options)

def test_update_disable_etcd():
    portworx_service()
    update_options = {
        "etcd": {
            "enabled": False
            }
        }

    update_service(update_options, False)

def update_service(options: dict, wait_for_kick_off=True):
    with tempfile.NamedTemporaryFile("w", suffix=".json") as f:
        options_path = f.name

        log.info("Writing updated options to %s", options_path)
        json.dump(options, f)
        f.flush()

        cmd = ["update", "start", "--options={}".format(options_path)]
        sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, " ".join(cmd))

        # An update plan is a deploy plan
        if wait_for_kick_off:
            sdk_plan.wait_for_kicked_off_deployment(config.SERVICE_NAME)
        sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)


@pytest.mark.install
@pytest.mark.authinstall
@pytest.mark.sanity
def test_uninstall_pxcleanup():
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.portworx_cleanup()

