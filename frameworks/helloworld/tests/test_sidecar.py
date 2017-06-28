import pytest

import sdk_install as install
import sdk_plan as plan
import sdk_utils

from tests.config import (
    PACKAGE_NAME
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    options = {
        "service": {
            "spec_file": "examples/sidecar.yml"
        }
    }

    # this yml has 2 hello's + 0 world's:
    install.install(PACKAGE_NAME, 2, additional_options=options)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_deploy():
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    deployment_plan = plan.get_deployment_plan(PACKAGE_NAME)
    sdk_utils.out("deployment plan: " + str(deployment_plan))

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


def run_plan(plan_name, params=None):
    plan.start_plan(PACKAGE_NAME, plan_name, params)

    started_plan = plan.get_plan(PACKAGE_NAME, plan_name)
    sdk_utils.out("sidecar plan: " + str(started_plan))
    assert(len(started_plan['phases']) == 1)
    assert(started_plan['phases'][0]['name'] == plan_name + '-deploy')
    assert(len(started_plan['phases'][0]['steps']) == 2)

    plan.wait_for_completed_plan(PACKAGE_NAME, plan_name)
