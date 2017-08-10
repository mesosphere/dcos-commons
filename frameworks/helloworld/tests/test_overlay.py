import json
import logging

import pytest

from shakedown.dcos.spinner import TimeoutExpired
import shakedown

import sdk_hosts
import sdk_install
import sdk_plan
import sdk_utils
import sdk_networks
import sdk_api

from dcos.http import DCOSHTTPException

from tests.config import (
    PACKAGE_NAME
)

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME)
        sdk_install.install(
            PACKAGE_NAME,
            1,
            additional_options={ "service": { "spec_file": "examples/overlay.yml" } })

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME)


# test suite constants
EXPECTED_TASKS = [
    'getter-0-get-host',
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
@sdk_utils.dcos_1_9_or_higher
def test_overlay_network():
    """Verify that the current deploy plan matches the expected plan from the spec."""

    deployment_plan = sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)
    log.info("deployment_plan: " + str(deployment_plan))

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

    # Due to DNS resolution flakiness, some of the deployed tasks can fail. If so,
    # we wait for them to redeploy, but if they don't fail we still want to proceed.
    try:
        sdk_plan.wait_for_in_progress_recovery(PACKAGE_NAME, timeout_seconds=60)
        sdk_plan.wait_for_completed_recovery(PACKAGE_NAME, timeout_seconds=60)
    except TimeoutExpired:
        pass

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

    sdk_networks.check_task_network("hello-overlay-0-server")
    sdk_networks.check_task_network("hello-overlay-vip-0-server")
    sdk_networks.check_task_network("hello-host-0-server", expected_network_name=None)
    sdk_networks.check_task_network("hello-host-vip-0-server", expected_network_name=None)

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
    assert overlay_endpoints_result["dns"][0] == sdk_hosts.autoip_host(PACKAGE_NAME, "hello-overlay-vip-0-server", 4044)

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
    assert host_endpoints_result["dns"][0] == sdk_hosts.autoip_host(PACKAGE_NAME, "hello-host-vip-0-server", 4044)


@pytest.mark.sanity
@pytest.mark.overlay
@sdk_utils.dcos_1_9_or_higher
def test_port_names():
    def check_task_ports(task_name, expected_port_count, expected_port_names):
        endpoint = "/v1/tasks/info/{}".format(task_name)
        try:
            r = sdk_api.get(PACKAGE_NAME, endpoint).json()
        except DCOSHTTPException:
            return False, "Failed to get API endpoint {}".format(endpoint)
        sdk_networks.check_port_names(r, expected_port_count, expected_port_names)

    for task in TASKS_WITH_PORTS:
        if task == "hello-overlay-0-server":
            check_task_ports(task, 2, ["dummy", "dynport"])
        else:
            check_task_ports(task, 1, ["test"])


@pytest.mark.sanity
@pytest.mark.overlay
@sdk_utils.dcos_1_9_or_higher
def test_srv_records():
    def check_port_record(task_records, task_name, record_name):
        record_name_prefix = "_{}.".format(record_name)
        matching_records = [r for r in task_records if r["name"].startswith(record_name_prefix)]
        assert len(matching_records) == 1, \
            "Missing SRV record for {} (prefix={}) in task {}:\nmatching={}\nall={}".format(
                record_name, record_name_prefix, task_name, matching_records, task_records)

    fmk_srvs = sdk_networks.get_framework_srv_records(PACKAGE_NAME)
    for task in TASKS_WITH_PORTS:
        task_records = sdk_networks.get_task_record(task, fmk_srvs)
        if task == "hello-overlay-0-server":
            check_port_record(task_records, task, "overlay-dummy")
            check_port_record(task_records, task, "overlay-dynport")
        elif task == "hello-host-vip-0-server":
            check_port_record(task_records, task, "host-vip")
        elif task == "hello-overlay-vip-0-server":
            check_port_record(task_records, task, "overlay-vip")
        elif task == "hello-host-0-server":
            check_port_record(task_records, task, "host-port")
        else:
            assert False, "Unknown task {}".format(task)
