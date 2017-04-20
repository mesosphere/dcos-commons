import pytest

import sdk_install as install
import sdk_plan as plan
import sdk_spin as spin
import sdk_utils

from tests.config import (
    PACKAGE_NAME
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    options = {
        "service": {
            "spec_file": "examples/parameterized-plan.yml"
        }
    }

    # this yml has 2 hello's + 0 world's:
    install.install(PACKAGE_NAME, 2, additional_options=options)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_deploy():
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    deployment_plan = plan.get_deployment_plan(PACKAGE_NAME).json()
    sdk_utils.out("deployment_plan: " + str(deployment_plan))

    assert(len(deployment_plan['phases']) == 2)
    assert(deployment_plan['phases'][0]['name'] == 'server-deploy')
    assert(deployment_plan['phases'][1]['name'] == 'once-deploy')
    assert(len(deployment_plan['phases'][0]['steps']) == 2)
    assert(len(deployment_plan['phases'][1]['steps']) == 2)


@pytest.mark.sanity
def test_sidecar():
    plan.start_sidecar_plan(PACKAGE_NAME, {'PLAN_PARAMETER': 'parameterized'})
    sidecar_plan = plan.get_sidecar_plan(PACKAGE_NAME).json()
    sdk_utils.out("sidecar_plan: " + str(sidecar_plan))

    assert(len(sidecar_plan['phases']) == 1)
    assert(sidecar_plan['phases'][0]['name'] == 'sidecar-deploy')
    assert(len(sidecar_plan['phases'][0]['steps']) == 2)

    spin.time_wait_noisy(lambda: (
        plan.get_sidecar_plan(PACKAGE_NAME).json()['status'] == 'COMPLETE'))
