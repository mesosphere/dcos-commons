import pytest
import json
import os

import shakedown

import sdk_hosts as hosts
import sdk_install as install
import sdk_plan as plan
import sdk_utils as utils
import sdk_networks as networks
import sdk_api as api

from dcos.http import DCOSHTTPException

from tests.config import (
    PACKAGE_NAME
)

overlay_nostrict = pytest.mark.skipif(os.environ.get("SECURITY") == "strict",
    reason="overlay tests currently broken in strict")

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


# test suite constants
EXPECTED_TASKS = ['getter-0-get-host',
                  'getter-0-get-overlay',
                  'getter-0-get-overlay-vip',
                  'getter-0-get-host-vip',
                  'hello-host-vip-0-server',
                  'hello-overlay-vip-0-server',
                  'hello-host-0-server',
                  'hello-overlay-0-server']


TASKS_WITH_PORTS = [task for task in EXPECTED_TASKS if "hello" in task]

@pytest.mark.sanity
@pytest.mark.overlay
@overlay_nostrict
@utils.dcos_1_9_or_higher
def test_overlay_network():
    """Verify that the current deploy plan matches the expected plan from the spec."""

    deployment_plan = plan.wait_for_completed_deployment(PACKAGE_NAME)
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

    for expected_task in EXPECTED_TASKS:
        assert(expected_task in framework_task_names), "Missing {expected}".format(expected=expected_task)

    for task in framework_tasks:
        name = task["name"]
        if "getter" in name:  # don't check the "getter" tasks because they don't use ports
            continue
        resources = task["resources"]
        if "host" in name:
            assert "ports" in resources.keys(), "Task {} should have port resources".format(name)
        if "overlay" in name:
            assert "ports" not in resources.keys(), "Task {} should NOT have port resources".format(name)

    networks.check_task_network("hello-overlay-0-server")
    networks.check_task_network("hello-overlay-vip-0-server")
    networks.check_task_network("hello-host-0-server", expected_network_name=None)
    networks.check_task_network("hello-host-vip-0-server", expected_network_name=None)

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
    assert overlay_endpoints_result["dns"][0] == hosts.autoip_host(PACKAGE_NAME, "hello-overlay-vip-0-server", 4044)

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
    assert host_endpoints_result["dns"][0] == hosts.autoip_host(PACKAGE_NAME, "hello-host-vip-0-server", 4044)


@pytest.mark.sanity
@pytest.mark.overlay
@overlay_nostrict
@utils.dcos_1_9_or_higher
def test_port_names():
    def check_task_ports(task_name, expected_port_count, expected_port_names):
        endpoint = "/v1/tasks/info/{}".format(task_name)
        try:
            r = api.get(PACKAGE_NAME, endpoint).json()
        except DCOSHTTPException:
            return False, "Failed to get API endpoint {}".format(endpoint)
        networks.check_port_names(r, expected_port_count, expected_port_names)

    for task in TASKS_WITH_PORTS:
        if task == "hello-overlay-0-server":
            check_task_ports(task, 2, ["dummy", "dynport"])
        else:
            check_task_ports(task, 1, ["test"])

@pytest.mark.sanity
@pytest.mark.overlay
@overlay_nostrict
@utils.dcos_1_9_or_higher
def test_srv_records():
    fmk_srvs = networks.get_framework_srv_records(PACKAGE_NAME)
    for task in TASKS_WITH_PORTS:
        task_records = networks.get_task_record(task, fmk_srvs)
        if task == "hello-overlay-0-server":
            assert len([r for r in task_records if "dummy" in r["name"]]) == 1, "Missing SRV record for dummy and "\
                "task {}".format(task)
            assert len([r for r in task_records if "dynport" in r["name"]]) == 1, "Missing SRV record for dynport "\
                "task {}".format(task)
        else:
            assert len([r for r in task_records if "test" in r["name"]]) == 1, "Missing SRV record for test and task"\
                "{}".format(task)
