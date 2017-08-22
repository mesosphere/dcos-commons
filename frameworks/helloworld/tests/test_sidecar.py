import logging

import pytest

import sdk_install
import sdk_plan
import sdk_marathon


from tests.config import (
    PACKAGE_NAME,
    SERVICE_NAME
)

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME, SERVICE_NAME)
        options = {
            "service": {
                "spec_file": "examples/sidecar.yml"
            }
        }

        # this yml has 2 hello's + 0 world's:
        sdk_install.install(PACKAGE_NAME, SERVICE_NAME, 2, additional_options=options)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME, SERVICE_NAME)


@pytest.mark.sanity
def test_deploy():
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)
    deployment_plan = sdk_plan.get_deployment_plan(SERVICE_NAME)
    log.info("deployment plan: " + str(deployment_plan))

    assert(len(deployment_plan['phases']) == 2)
    assert(deployment_plan['phases'][0]['name'] == 'server-deploy')
    assert(deployment_plan['phases'][1]['name'] == 'once-deploy')
    assert(len(deployment_plan['phases'][0]['steps']) == 2)
    assert(len(deployment_plan['phases'][1]['steps']) == 2)


@pytest.mark.sanity
def test_sidecar():
    run_plan('sidecar')


@pytest.mark.sanity
def test_sidecar_parameterized():
    run_plan('sidecar-parameterized', {'PLAN_PARAMETER': 'parameterized'})


@pytest.mark.sanity
def test_toxic_sidecar_doesnt_trigger_recovery():
    # 1. Run the toxic sidecar plan that will never succeed.
    # 2. Restart the scheduler.
    # 3. Verify that its recovery plan is empty, as a failed FINISHED task should
    # never trigger recovery
    recovery_plan = sdk_plan.get_plan(SERVICE_NAME, 'recovery')
    assert(len(recovery_plan['phases']) == 0)
    log.info(recovery_plan)
    sdk_plan.start_plan(SERVICE_NAME, 'sidecar-toxic')
    # Wait for the bad sidecar plan to be starting.
    sdk_plan.wait_for_starting_plan(SERVICE_NAME, 'sidecar-toxic')

    # Restart the scheduler and wait for it to come up.
    sdk_marathon.restart_app(SERVICE_NAME)
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Now, verify that its recovery plan is empty.
    sdk_plan.wait_for_completed_plan(SERVICE_NAME, 'recovery')
    recovery_plan = sdk_plan.get_plan(SERVICE_NAME, 'recovery')
    assert(len(recovery_plan['phases']) == 0)


def run_plan(plan_name, params=None):
    sdk_plan.start_plan(SERVICE_NAME, plan_name, params)

    started_plan = sdk_plan.get_plan(SERVICE_NAME, plan_name)
    log.info("sidecar plan: " + str(started_plan))
    assert(len(started_plan['phases']) == 1)
    assert(started_plan['phases'][0]['name'] == plan_name + '-deploy')
    assert(len(started_plan['phases'][0]['steps']) == 2)

    sdk_plan.wait_for_completed_plan(SERVICE_NAME, plan_name)
