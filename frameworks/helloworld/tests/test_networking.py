import pytest

import sdk_install as install
import sdk_plan as plan
import sdk_spin as spin
import sdk_utils as utils

from tests.config import (
    PACKAGE_NAME
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    options = {
        "service": {
            "spec_file": "examples/cni.yml"
        }
    }

    install.install(PACKAGE_NAME, 1, additional_options=options)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.cni
@pytest.mark.smoke
def test_deploy():
    """Verify that the current deploy plan matches the expected plan from the spec."""
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    deployment_plan = plan.get_deployment_plan(PACKAGE_NAME).json()
    utils.out("deployment_plan: " + str(deployment_plan))

    # deploy two pods serially
    assert(len(deployment_plan['phases']) == 2)
    assert(deployment_plan['phases'][0]['name'] == 'hello-deploy')
    assert(deployment_plan["phases"][1]["name"] == "world-deploy")

    # they both have two steps, network-task and server
    assert(len(deployment_plan['phases'][0]['steps']) == 2)
    assert(len(deployment_plan["phases"][1]["steps"]) == 2)


@pytest.mark.sanity
@pytest.mark.cni
@pytest.mark.smoke
def test_joins_overlay_network():
    """Verify that the container joined the dcos subnet at 9.0.0.0/24.

    The logic for this is in the task itself, which will check the container IP address
    and fail if incorrect, thus preventing the plan from reaching the COMPLETE state."""
    plan.wait_for_completed_deployment(PACKAGE_NAME)
