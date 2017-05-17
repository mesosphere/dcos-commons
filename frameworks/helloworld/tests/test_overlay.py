import pytest

import shakedown
import dcos

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
            "spec_file": "examples/overlay.yml"
        }
    }

    install.install(PACKAGE_NAME, 1, additional_options=options)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.overlay
def test_overlay_network():
    """Verify that the current deploy plan matches the expected plan from the spec."""
    def check_task_network(task_name, on_overlay, expected_network_name="dcos"):
        _task = shakedown.get_task(task_id=task_name, completed=False)
        for status in _task["statuses"]:
            if status["state"] == "TASK_RUNNING":
                for network_info in status["container_status"]["network_infos"]:
                    if on_overlay:
                        assert "name" in network_info, \
                            "Didn't find network name in NetworkInfo for task {task} with "\
                            "status {status}".format(task=task_name, status=status)
                        assert network_info["name"] == expected_network_name, \
                            "Expected network name {expected} found {observed}"\
                            .format(expected=expected_network_name, observed=network_info["name"])
                    else:
                        assert "name" not in network_info, \
                            "Task {task} has network name when it shouldn't has status {status}"\
                            .format(task=task_name, status=status)

    plan.wait_for_completed_deployment(PACKAGE_NAME)
    deployment_plan = plan.get_deployment_plan(PACKAGE_NAME).json()
    utils.out("deployment_plan: " + str(deployment_plan))

    # test that the deployment plan is correct
    assert(len(deployment_plan['phases']) == 3)
    assert(deployment_plan['phases'][0]['name'] == 'hello-overlay-deploy')
    assert(deployment_plan['phases'][1]['name'] == 'hello-host-deploy')
    assert(deployment_plan["phases"][2]["name"] == "getter-deploy")
    assert(len(deployment_plan['phases'][0]['steps']) == 1)
    assert(len(deployment_plan["phases"][1]["steps"]) == 1)
    assert(len(deployment_plan["phases"][2]["steps"]) == 2)

    # test that the tasks are all up, which tests the overlay DNS
    framework_tasks = [task["name"] for task in shakedown.get_service_tasks(PACKAGE_NAME, completed=False)]
    expected_tasks = ['getter-0-get-host',
                      'getter-0-get-overlay',
                      'hello-host-0-server',
                      'hello-overlay-0-server']

    for expected_task in expected_tasks:
        assert(expected_task in framework_tasks), "Missing {expected}".format(expected=expected_task)

    check_task_network("getter-0-get-host", True)
    check_task_network("getter-0-get-overlay", True)
    check_task_network("hello-overlay-0-server", True)
    check_task_network("hello-host-0-server", False)


