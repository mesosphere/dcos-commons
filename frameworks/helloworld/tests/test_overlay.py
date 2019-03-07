import logging
from typing import Dict, Iterator, List

import json
import pytest
import retrying

import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_networks
import sdk_plan
import sdk_tasks

from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security: None) -> Iterator[None]:
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            4,  # only wait for 4: getter-0-check-comm may enter FINISHED state
            additional_options={"service": {"yaml": "overlay"}},
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.smoke
@pytest.mark.dcos_min_version("1.9")
def test_overlay_network() -> None:
    """Verify that the current deploy plan matches the expected plan from the spec."""

    deployment_plan = sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    log.info(sdk_plan.plan_string("deploy", deployment_plan))

    # test that the tasks are all up, which tests the overlay DNS
    framework_tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME)

    expected_running_tasks = [
        "overlay-vip-0-server",
        "overlay-0-server",
        "host-vip-0-server",
        "host-0-server"
    ]
    assert set(expected_running_tasks) == set([t.name for t in framework_tasks])

    for task in framework_tasks:
        name = task.name
        if name.startswith("host-"):
            assert "ports" in task.resources.keys(), "Task {} should have port resources".format(
                name
            )
            sdk_networks.check_task_network(name, expected_network_name=None)
        elif name.startswith("overlay-"):
            assert (
                "ports" not in task.resources.keys()
            ), "Task {} should NOT have port resources".format(
                name
            )
            sdk_networks.check_task_network(name)
        else:
            assert False, "Unknown task {}".format(name)

    endpoints_result = sdk_networks.get_endpoint_names(config.PACKAGE_NAME, config.SERVICE_NAME)
    assert len(endpoints_result) == 2, "Expected 2 endpoints, got: {}".format(endpoints_result)

    overlay_endpoints_result = sdk_networks.get_endpoint(
        config.PACKAGE_NAME, config.SERVICE_NAME, "overlay-vip"
    )
    assert "address" in overlay_endpoints_result.keys(), (
        "overlay endpoints missing 'address': {}".format(overlay_endpoints_result)
    )
    assert len(overlay_endpoints_result["address"]) == 1
    assert overlay_endpoints_result["address"][0].startswith("9")
    overlay_port = overlay_endpoints_result["address"][0].split(":")[-1]
    assert overlay_port == "4044"
    assert "dns" in overlay_endpoints_result.keys()
    assert len(overlay_endpoints_result["dns"]) == 1
    assert overlay_endpoints_result["dns"][0] == sdk_hosts.autoip_host(
        config.SERVICE_NAME, "overlay-vip-0-server", 4044
    )

    host_endpoints_result = sdk_networks.get_endpoint(
        config.PACKAGE_NAME, config.SERVICE_NAME, "host-vip"
    )
    assert "address" in host_endpoints_result.keys(), (
        "overlay endpoints missing 'address'" "{}".format(host_endpoints_result)
    )
    assert len(host_endpoints_result["address"]) == 1
    assert host_endpoints_result["address"][0].startswith("10")
    host_port = host_endpoints_result["address"][0].split(":")[-1]
    assert host_port == "4044"
    assert "dns" in host_endpoints_result.keys()
    assert len(host_endpoints_result["dns"]) == 1
    assert host_endpoints_result["dns"][0] == sdk_hosts.autoip_host(
        config.SERVICE_NAME, "host-vip-0-server", 4044
    )


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_cni_labels():
    def check_labels(labels, idx):
        k = labels[idx]["key"]
        v = labels[idx]["value"]

        expected_network_labels = {"key0": "val0", "key1": "val1"}
        assert k in expected_network_labels.keys(), "Got unexpected network key {}".format(k)
        assert v == expected_network_labels[k], (
            "Value {obs} isn't correct, should be "
            "{exp}".format(obs=v, exp=expected_network_labels[k])
        )

    r = sdk_cmd.service_request(
        "GET", config.SERVICE_NAME, "/v1/pod/overlay-vip-0/info"
    ).json()
    assert len(r) == 1, "Got multiple responses from v1/pod/overlay-vip-0/info"
    try:
        cni_labels = r[0]["info"]["executor"]["container"]["networkInfos"][0]["labels"]["labels"]
    except KeyError:
        assert False, "CNI labels not present"
    assert len(cni_labels) == 2, "Got {} labels, should be 2".format(len(cni_labels))
    for i in range(2):
        try:
            check_labels(cni_labels, i)
        except KeyError:
            assert False, "Couldn't get CNI labels from {}".format(cni_labels)


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_srv_records():

    # getter-0-check-comm lacks ports and should not be present in the SRV records:
    task_to_expected_port_names = {
        "overlay-vip-0-server": ["overlay-vip"],
        "overlay-0-server": ["overlay-dummy", "overlay-dynport"],
        "host-vip-0-server": ["host-vip"],
        "host-0-server": ["host-port"]
    }

    def check_expected_srv_records(task_to_srv_names: Dict[str, List[str]]) -> None:
        assert task_to_expected_port_names.keys() == task_to_srv_names.keys(), "Mismatch between expected and actual tasks"
        for task_name, srv_names in task_to_srv_names.items():
            expected_port_names = task_to_expected_port_names[task_name]
            # For each expected_port_name, search for a matching srv_name:
            for expected_port_name in expected_port_names:
                expected_record_name = "_{}._{}._tcp.{}.mesos.".format(expected_port_name, task_name, config.SERVICE_NAME)
                assert expected_record_name in srv_names

    log.info("Getting framework srv records for %s", config.SERVICE_NAME)

    # wait for up to 5 minutes for SRV records to settle down.
    # sometimes individual task entries don't appear right away.
    @retrying.retry(
        stop_max_delay=5 * 60 * 1000,
        wait_exponential_multiplier=1000,
        wait_exponential_max=120 * 1000,
    )
    def wait_for_valid_srv_records() -> None:
        cmd = "curl localhost:8123/v1/enumerate"
        rc, stdout, _ = sdk_cmd.master_ssh(cmd)
        assert rc == 0, "Failed to get srv records from master SSH: {}".format(cmd)
        try:
            srvs = json.loads(stdout)
        except Exception:
            log.exception("Failed to parse JSON endpoints: %s", stdout)
            raise

        try:
            # find the framework matching our expected name which has one or more tasks.
            # we can end up with "duplicate" frameworks left over from previous tests where the framework didn't successfully unregister.
            # in practice these "duplicate"s will appear as a framework entry with an empty list of tasks.
            framework_srvs = [
                f for f in srvs["frameworks"] if f["name"] == config.SERVICE_NAME and len(f["tasks"]) > 0
            ]
            assert len(framework_srvs) == 1, "Expected exactly one entry for service {}: {}".format(
                config.SERVICE_NAME, framework_srvs
            )
            framework_srv = framework_srvs[0]
            assert "tasks" in framework_srv, "Framework SRV records missing 'tasks': {}".format(
                framework_srv
            )

            # Mapping of task_name => [srv_name_1, srv_name_2, ...]
            task_to_srv_names: Dict[str, List[str]] = {}
            for t in framework_srv["tasks"]:
                if t["name"] in task_to_srv_names:
                    assert False, "Got multiple entries for task {}: {}".format(t["name"], framework_srv)
                task_to_srv_names[t["name"]] = [r["name"] for r in t["records"]]

            check_expected_srv_records(task_to_srv_names)
        except Exception:
            # Log the assert message before retrying (or giving up)
            log.exception("SRV record validation failed, trying again...")
            raise
    wait_for_valid_srv_records()
