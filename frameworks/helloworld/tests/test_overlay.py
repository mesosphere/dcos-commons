import pytest
import json

import shakedown

import sdk_install as install
import sdk_plan as plan
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


@pytest.mark.sanity
@pytest.mark.smoke
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
                            "status:{status}".format(task=task_name, status=status)
                        assert network_info["name"] == expected_network_name, \
                            "Expected network name:{expected} found:{observed}"\
                            .format(expected=expected_network_name, observed=network_info["name"])
                    else:
                        assert "name" not in network_info, \
                            "Task {task} has network name when it shouldn't has status:{status}"\
                            .format(task=task_name, status=status)

    plan.wait_for_completed_deployment(PACKAGE_NAME)
    deployment_plan = plan.get_deployment_plan(PACKAGE_NAME).json()
    utils.out("deployment_plan: " + str(deployment_plan))

    # test that the deployment plan is correct
    assert(len(deployment_plan['phases']) == 5)
    assert(deployment_plan['phases'][0]['name'] == 'hello-overlay-vip-deploy')
    assert(deployment_plan['phases'][1]['name'] == 'hello-overlay-deploy')
    assert(deployment_plan['phases'][2]['name'] == 'hello-host-vip-deploy')
    assert(deployment_plan['phases'][3]['name'] == 'hello-host-deploy')
    assert(deployment_plan["phases"][4]["name"] == "getter-deploy")
    assert(len(deployment_plan['phases'][0]['steps']) == 1)
    assert(len(deployment_plan["phases"][1]["steps"]) == 1)
    assert(len(deployment_plan["phases"][2]["steps"]) == 1)
    assert(len(deployment_plan["phases"][3]["steps"]) == 1)
    assert(len(deployment_plan["phases"][4]["steps"]) == 4)

    # test that the tasks are all up, which tests the overlay DNS
    framework_tasks = [task for task in shakedown.get_service_tasks(PACKAGE_NAME, completed=False)]
    framework_task_names = [t["name"] for t in framework_tasks]
    expected_tasks = ['getter-0-get-Host',
                      'getter-0-get-Overlay',
                      'getter-0-get-Overlay-vip',
                      'getter-0-get-Host-vip',
                      'hello-host-vip-0-server',
                      'hello-overlay-vip-0-server',
                      'hello-host-0-server',
                      'hello-overlay-0-server']

    for expected_task in expected_tasks:
        assert(expected_task in framework_task_names), "Missing {expected}".format(expected=expected_task)

    for task in framework_tasks:
        name = task["name"]
        resources = task["resources"]
        if "host" in name:
            assert "ports" in resources.keys(), "Task {} should have port resources".format(name)
        if "overlay" in name:
            assert "ports" not in resources.keys(), "Task {} should NOT have port resources".format(name)

    check_task_network("hello-overlay-0-server", True)
    check_task_network("hello-overlay-vip-0-server", True)
    check_task_network("hello-host-0-server", False)
    check_task_network("hello-host-vip-0-server", False)

    endpoints_result, _, rc = shakedown.run_dcos_command("{pkg} endpoints".format(pkg=PACKAGE_NAME))
    endpoints_result = json.loads(endpoints_result)
    assert rc == 0, "Getting endpoints failed"
    assert len(endpoints_result) == 2, "Wrong number of endpoints got {} should be 2".format(len(endpoints_result))


    overlay_endpoints_result, _, rc = shakedown.run_dcos_command("{pkg} endpoints overlay-vip".format(pkg=PACKAGE_NAME))
    assert rc == 0, "Getting overlay endpoints failed"
    overlay_endpoints_result = json.loads(overlay_endpoints_result)
    assert "address" in overlay_endpoints_result.keys(), "overlay endpoints missing 'address'"\
           "{}".format(overlay_endpoints_result)
    assert len(overlay_endpoints_result["address"]) == 1
    assert overlay_endpoints_result["address"][0].startswith("9")
    overlay_port = overlay_endpoints_result["address"][0].split(":")[-1]
    assert overlay_port == "4044"
    assert "dns" in overlay_endpoints_result.keys()
    assert len(overlay_endpoints_result["dns"]) == 1
    assert overlay_endpoints_result["dns"][0] == "hello-overlay-vip-0-server.hello-world.autoip.dcos.thisdcos.directory:4044"


    host_endpoints_result, _, rc = shakedown.run_dcos_command("{pkg} endpoints host-vip".format(pkg=PACKAGE_NAME))
    assert rc == 0, "Getting host endpoints failed"
    host_endpoints_result = json.loads(host_endpoints_result)
    assert "address" in host_endpoints_result.keys(), "overlay endpoints missing 'address'"\
           "{}".format(host_endpoints_result)
    assert len(host_endpoints_result["address"]) == 1
    assert host_endpoints_result["address"][0].startswith("10")
    host_port = host_endpoints_result["address"][0].split(":")[-1]
    assert host_port == "4044"
    assert "dns" in host_endpoints_result.keys()
    assert len(host_endpoints_result["dns"]) == 1
    assert host_endpoints_result["dns"][0] == "hello-host-vip-0-server.hello-world.autoip.dcos.thisdcos.directory:4044"
