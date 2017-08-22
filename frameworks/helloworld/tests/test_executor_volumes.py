import logging

import pytest
import sdk_install
import sdk_plan
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {
            "service": {
                "spec_file": "examples/executor_volume.yml"
            }
        }

        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 3, additional_options=options)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.executor_volumes
def test_deploy():
    deployment_plan = sdk_plan.get_deployment_plan(config.SERVICE_NAME)
    log.info("deployment plan: " + str(deployment_plan))

    assert(len(deployment_plan['phases']) == 3)
    assert(deployment_plan['phases'][0]['name'] == 'hello-deploy')
    assert(deployment_plan['phases'][1]['name'] == 'world-server-deploy')
    assert(deployment_plan['phases'][2]['name'] == 'world-once-deploy')
    assert(len(deployment_plan['phases'][0]['steps']) == 2)
    assert(len(deployment_plan['phases'][1]['steps']) == 1)
    assert(len(deployment_plan['phases'][2]['steps']) == 1)


@pytest.mark.sanity
@pytest.mark.executor_volumes
def test_sidecar():
    sdk_plan.start_plan(config.SERVICE_NAME, 'sidecar')

    started_plan = sdk_plan.get_plan(config.SERVICE_NAME, 'sidecar')
    log.info("sidecar plan: " + str(started_plan))
    assert(len(started_plan['phases']) == 1)
    assert(started_plan['phases'][0]['name'] == 'sidecar-deploy')
    assert(len(started_plan['phases'][0]['steps']) == 2)

    sdk_plan.wait_for_completed_plan(config.SERVICE_NAME, 'sidecar')
