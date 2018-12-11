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

    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=sdk_install.merge_dictionaries(options, config.PX_NODE_OPTIONS),
            wait_for_deployment=True)

        # Wait for service health check to pass
        shakedown.service_healthy(config.SERVICE_NAME)

        yield {**options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.portworx_cleanup()

@pytest.mark.sanity
def test_update_node_count():
    portworx_service()
    update_options = {
        "node": {
            "count": 3
            }
        }

    update_service(update_options)

@pytest.mark.sanity
def test_update_px_image():
    portworx_service()
    px_image = os.environ['PX_IMAGE']
    update_options = {
        "node": {
            "portworx_image": px_image
            }
        }

    update_service(update_options)

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

@pytest.mark.sanity
def test_update_enable_etcd():
    portworx_service()
    update_options = {
        "etcd": {
            "enabled": True
            }
        }

    update_service(update_options)

@pytest.mark.sanity
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
