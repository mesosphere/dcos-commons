import pytest

import shakedown
import sdk_install as install
import sdk_plan as plan
import sdk_utils

from tests.config import (
    PACKAGE_NAME
)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        install.uninstall(PACKAGE_NAME)
        options = {
            "service": {
                "spec_file": "examples/executor_volume.yml"
            }
        }

        install.install(PACKAGE_NAME, 3, additional_options=options)

        yield # let the test session execute
    finally:
        install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.executor_volumes
def test_deploy():
    deployment_plan = plan.get_deployment_plan(PACKAGE_NAME)
    sdk_utils.out("deployment plan: " + str(deployment_plan))

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
    plan.start_plan(PACKAGE_NAME, 'sidecar')

    started_plan = plan.get_plan(PACKAGE_NAME, 'sidecar')
    sdk_utils.out("sidecar plan: " + str(started_plan))
    assert(len(started_plan['phases']) == 1)
    assert(started_plan['phases'][0]['name'] == 'sidecar-deploy')
    assert(len(started_plan['phases'][0]['steps']) == 2)

    plan.wait_for_completed_plan(PACKAGE_NAME, 'sidecar')
