import logging

import json
import pytest
import retrying

import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_networks
import sdk_plan
import shakedown
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            4,
            additional_options={"service": {"yaml": "overlay"}})

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


EXPECTED_TASKS = [
    'hello-host-vip-0-server',
    'hello-overlay-vip-0-server',
    'hello-host-0-server',
    'hello-overlay-0-server']

EXPECTED_NETWORK_LABELS = {
    "key0": "val0",
    "key1": "val1"
}


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.smoke
@pytest.mark.dcos_min_version('1.9')
def test_overlay_network():
    """Verify that the current deploy plan matches the expected plan from the spec."""

    deployment_plan = sdk_plan.wait_for_completed_deployment(
        config.SERVICE_NAME)
    log.info("deployment_plan: " + str(deployment_plan))

    # test that the deployment plan is correct
    assert(len(deployment_plan['phases']) == 5)
    assert(deployment_plan['phases'][0]['name'] == 'hello-overlay-deploy')
    assert(deployment_plan['phases'][1]['name'] == 'hello-overlay-vip-deploy')
    assert(deployment_plan['phases'][2]['name'] == 'hello-host-vip-deploy')
    assert(deployment_plan['phases'][3]['name'] == 'hello-host-deploy')
    assert(deployment_plan["phases"][4]["name"] == "getter-deploy")
    assert(len(deployment_plan['phases'][0]['steps']) == 1)
    assert(len(deployment_plan["phases"][1]["steps"]) == 1)
    assert(len(deployment_plan["phases"][2]["steps"]) == 1)
    assert(len(deployment_plan["phases"][3]["steps"]) == 1)
    assert(len(deployment_plan["phases"][4]["steps"]) == 1)

    # Due to DNS resolution flakiness, some of the deployed tasks can fail. If so,
    # we wait for them to redeploy, but if they don't fail we still want to proceed.
    try:
        sdk_plan.wait_for_in_progress_recovery(
            config.SERVICE_NAME, timeout_seconds=60)
        sdk_plan.wait_for_completed_recovery(
            config.SERVICE_NAME, timeout_seconds=60)
    except retrying.RetryError:
        pass

    # test that the tasks are all up, which tests the overlay DNS
    framework_tasks = [task for task in shakedown.get_service_tasks(
        config.SERVICE_NAME, completed=False)]
    framework_task_names = [t["name"] for t in framework_tasks]

    for expected_task in EXPECTED_TASKS:
        assert(expected_task in framework_task_names), "Missing {expected}".format(
            expected=expected_task)

    for task in framework_tasks:
        name = task["name"]
        if "getter" in name:  # don't check the "getter" tasks because they don't use ports
            continue
        resources = task["resources"]
        if "host" in name:
            assert "ports" in resources.keys(), "Task {} should have port resources".format(name)
        if "overlay" in name:
            assert "ports" not in resources.keys(
            ), "Task {} should NOT have port resources".format(name)

    sdk_networks.check_task_network("hello-overlay-0-server")
    sdk_networks.check_task_network("hello-overlay-vip-0-server")
    sdk_networks.check_task_network(
        "hello-host-0-server", expected_network_name=None)
    sdk_networks.check_task_network(
        "hello-host-vip-0-server", expected_network_name=None)

    endpoints_result = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'endpoints', json=True)
    assert len(endpoints_result) == 2, "Wrong number of endpoints got {} should be 2".format(
        len(endpoints_result))

    overlay_endpoints_result = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'endpoints overlay-vip', json=True)
    assert "address" in overlay_endpoints_result.keys(), "overlay endpoints missing 'address'"\
           "{}".format(overlay_endpoints_result)
    assert len(overlay_endpoints_result["address"]) == 1
    assert overlay_endpoints_result["address"][0].startswith("9")
    overlay_port = overlay_endpoints_result["address"][0].split(":")[-1]
    assert overlay_port == "4044"
    assert "dns" in overlay_endpoints_result.keys()
    assert len(overlay_endpoints_result["dns"]) == 1
    assert overlay_endpoints_result["dns"][0] == sdk_hosts.autoip_host(
        config.SERVICE_NAME, "hello-overlay-vip-0-server", 4044)

    host_endpoints_result = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'endpoints host-vip', json=True)
    assert "address" in host_endpoints_result.keys(), "overlay endpoints missing 'address'"\
           "{}".format(host_endpoints_result)
    assert len(host_endpoints_result["address"]) == 1
    assert host_endpoints_result["address"][0].startswith("10")
    host_port = host_endpoints_result["address"][0].split(":")[-1]
    assert host_port == "4044"
    assert "dns" in host_endpoints_result.keys()
    assert len(host_endpoints_result["dns"]) == 1
    assert host_endpoints_result["dns"][0] == sdk_hosts.autoip_host(
        config.SERVICE_NAME, "hello-host-vip-0-server", 4044)


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version('1.9')
def test_cni_labels():
    def check_labels(labels, idx):
        k = labels[idx]["key"]
        v = labels[idx]["value"]
        assert k in EXPECTED_NETWORK_LABELS.keys(
        ), "Got unexpected network key {}".format(k)
        assert v == EXPECTED_NETWORK_LABELS[k], "Value {obs} isn't correct, should be " \
                                                "{exp}".format(
                                                    obs=v, exp=EXPECTED_NETWORK_LABELS[k])

    r = sdk_cmd.service_request(
        'GET', config.SERVICE_NAME, "/v1/pod/hello-overlay-vip-0/info").json()
    assert len(
        r) == 1, "Got multiple responses from v1/pod/hello-overlay-vip-0/info"
    try:
        cni_labels = r[0]["info"]["executor"]["container"]["networkInfos"][0]["labels"]["labels"]
    except KeyError:
        assert False, "CNI labels not present"
    assert len(cni_labels) == 2, "Got {} labels, should be 2".format(
        len(cni_labels))
    for i in range(2):
        try:
            check_labels(cni_labels, i)
        except KeyError:
            assert False, "Couldn't get CNI labels from {}".format(cni_labels)


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version('1.9')
def test_srv_records():
    def get_task_record(task_name, fmk_srv_records):
        assert "tasks" in fmk_srv_records, "Framework SRV records missing 'tasks': {}".format(
            fmk_srv_records)
        task_records = [t for t in fmk_srv_records["tasks"]
                        if t["name"] == task_name]
        assert len(task_records) > 0, "Didn't find task record for {}".format(
            task_name)
        assert len(task_records) == 1, "Got redundant tasks for {}".format(
            task_name)
        task_record = task_records[0]
        assert "records" in task_record, "Task record {} missing 'records'".format(
            task_record)
        return task_record["records"]

    def check_port_record(task_records, task_name, record_name):
        record_name_prefix = "_{}.".format(record_name)
        matching_records = [
            r for r in task_records if r["name"].startswith(record_name_prefix)]
        assert len(matching_records) == 1, \
            "Missing SRV record for {} (prefix={}) in task {}:\nmatching={}\nall={}".format(
                record_name, record_name_prefix, task_name, matching_records, task_records)

    log.info("Getting framework srv records for %s", config.SERVICE_NAME)

    @retrying.retry(wait_exponential_multiplier=1000,
                    wait_exponential_max=120 * 1000)
    def call_shakedown():
        cmd = "curl localhost:8123/v1/enumerate"
        log.info("Running '%s' on master", cmd)
        is_ok, out = shakedown.run_command_on_master(cmd)
        log.info("Running command returned: is_ok=%s", is_ok)
        assert is_ok, "Failed to get srv records. command was {}".format(cmd)
        try:
            srvs = json.loads(out)
        except Exception as e:
            log.error("Error converting out=%s to json", out)
            log.error(e)
            raise e

        return srvs

    srvs = call_shakedown()
    framework_srvs = [f for f in srvs["frameworks"]
                      if f["name"] == config.SERVICE_NAME]
    assert len(framework_srvs) == 1, "Got too many srv records matching service {}, got {}"\
        .format(config.SERVICE_NAME, framework_srvs)
    framework_srv = framework_srvs[0]

    for task_name in EXPECTED_TASKS:
        assert "tasks" in framework_srv, "Framework SRV records missing 'tasks': {}".format(
            framework_srv)
        match_records = [t for t in framework_srv["tasks"]
                         if t["name"] == task_name]
        assert len(match_records) > 0, "Didn't find task record for {}".format(
            task_name)
        assert len(match_records) == 1, "Got redundant tasks for {}".format(
            task_name)
        task_records = match_records[0]["records"]
        if task_name == "hello-overlay-0-server":
            check_port_record(task_records, task_name, "overlay-dummy")
            check_port_record(task_records, task_name, "overlay-dynport")
        elif task_name == "hello-host-vip-0-server":
            check_port_record(task_records, task_name, "host-vip")
        elif task_name == "hello-overlay-vip-0-server":
            check_port_record(task_records, task_name, "overlay-vip")
        elif task_name == "hello-host-0-server":
            check_port_record(task_records, task_name, "host-port")
        else:
            assert False, "Unknown task {}".format(task_name)
