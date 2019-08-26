"""
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_networks IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import json as jsonlib
import logging
import retrying
from typing import Any, List, Optional

import sdk_agents
import sdk_cmd
import sdk_tasks


log = logging.getLogger(__name__)


ENABLE_VIRTUAL_NETWORKS_OPTIONS = {"service": {"virtual_network_enabled": True}}


@retrying.retry(wait_fixed=1000, stop_max_delay=5 * 1000, retry_on_result=lambda res: not res)
def _wait_for_endpoint_info(
    package_name: str, service_name: str, endpoint_name: str, json: bool
) -> Any:

    cmd = " ".join(part for part in ["endpoints", endpoint_name] if part)
    rc, stdout, _ = sdk_cmd.svc_cli(package_name, service_name, cmd)
    assert rc == 0, "Failed to get endpoint named {}".format(endpoint_name)

    if json:
        return jsonlib.loads(stdout)
    return stdout


def get_endpoint_names(package_name: str, service_name: str) -> List[str]:
    """Returns a list of endpoint names for the specified service."""
    result = _wait_for_endpoint_info(package_name, service_name, None, json=True)
    assert isinstance(result, list)
    return result


def get_endpoint(package_name: str, service_name: str, endpoint_name: str) -> Any:
    """Returns the content of the specified endpoint definition as a JSON object.

    {
      "address": [
        "10.0.1.92:1025",
        "10.0.3.95:1025",
        "10.0.1.0:1025"
      ],
      "dns": [
        "pod-0-task.service.autoip.dcos.thisdcos.directory:1025",
        "pod-1-task.service.autoip.dcos.thisdcos.directory:1025",
        "pod-2-task.service.autoip.dcos.thisdcos.directory:1025"
      ],
      "vip": "task.service.l4lb.thisdcos.directory:9092"
    }
    """
    # Catch if an empty string is passed in. Technically the command would succeed and return a list of endpoint names,
    # but they should use get_endpoint_names() for this.
    assert (
        endpoint_name
    ), "Missing endpoint_name. To get list of endpoint names, use get_endpoint_names()."

    return _wait_for_endpoint_info(package_name, service_name, endpoint_name, True)


def get_endpoint_string(package_name: str, service_name: str, endpoint_name: str) -> str:
    """Returns the content of the specified custom endpoint definition as a string.
    """
    # Catch if an empty string is passed in. Technically the command would succeed and return a list of endpoint names,
    # but they should use get_endpoint_names() for this.
    assert (
        endpoint_name
    ), "Missing endpoint_name. To get list of endpoint names, use get_endpoint_names()."

    info = _wait_for_endpoint_info(package_name, service_name, endpoint_name, False)
    assert isinstance(info, str)
    return info.strip()


def check_task_network(task_name: str, expected_network_name: Optional[str] = "dcos") -> None:
    """Tests whether a task (and it's parent pod) is on a given network
    """
    statuses = sdk_tasks.get_all_status_history(task_name, with_completed_tasks=False)
    assert len(statuses) != 0, "Unable to find any statuses for running task_name={}".format(
        task_name
    )

    for status in statuses:
        if status["state"] != "TASK_RUNNING":
            continue
        for network_info in status["container_status"]["network_infos"]:
            if expected_network_name is not None:
                assert "name" in network_info, (
                    "Didn't find network name in NetworkInfo for task {task} with "
                    "status:{status}".format(task=task_name, status=status)
                )
                assert (
                    network_info["name"] == expected_network_name
                ), "Expected network name:{expected} found:{observed}".format(
                    expected=expected_network_name, observed=network_info["name"]
                )
            else:
                assert (
                    "name" not in network_info
                ), "Task {task} has network name when it shouldn't, status:{status}".format(
                    task=task_name, status=status
                )


def check_endpoint_on_overlay(
    package_name: str, service_name: str, endpoint_to_get: str, expected_task_count: int
) -> None:
    endpoint = get_endpoint(package_name, service_name, endpoint_to_get)

    assert "address" in endpoint, "Missing 'address': {}".format(endpoint)
    endpoint_address = endpoint["address"]
    assert len(endpoint_address) == expected_task_count

    assert "dns" in endpoint, "Missing 'dns': {}".format(endpoint)
    endpoint_dns = endpoint["dns"]
    assert len(endpoint_dns) == expected_task_count

    # Addresses should have ip:port format:
    ip_addresses = set([e.split(":")[0] for e in endpoint_address])

    all_agent_ips = set([agent["hostname"] for agent in sdk_agents.get_agents()])
    assert (
        len(ip_addresses.intersection(all_agent_ips)) == 0
    ), "Overlay IPs should not match any agent IPs"

    for dns in endpoint_dns:
        assert (
            "autoip.dcos.thisdcos.directory" in dns
        ), "Expected 'autoip.dcos.thisdcos.directory' in DNS entry"


def get_task_host(task):
    agent_id = task["slave_id"]
    log.info("Retrieving agents information for {}".format(agent_id))
    agents = sdk_cmd.cluster_request("GET", "/mesos/slaves?slave_id={}".format(agent_id)).json()
    assert (
        len(agents["slaves"]) == 1
    ), "Agent's details do not match the expectations for agent ID {}".format(agent_id)
    return agents["slaves"][0]["hostname"]


def get_task_ip(task):
    task_running_status = None
    for status in task["statuses"]:
        if status["state"] == "TASK_RUNNING":
            task_running_status = status
            break

    assert task_running_status is not None, "No TASK_RUNNING status found for task: {}".format(task)

    ip_address = task_running_status["container_status"]["network_infos"][0]["ip_addresses"][0][
        "ip_address"
    ]
    assert (
        ip_address is not None and ip_address
    ), "Running task IP address is not defined for task status: {}".format(task_running_status)
    return ip_address


def get_overlay_subnet(network_name="dcos"):
    subnet = None
    network_info = sdk_cmd.cluster_request("GET", "/mesos/overlay-master/state").json()
    for network in network_info["network"]["overlays"]:
        if network["name"] == network_name:
            subnet = network["subnet"]
            break

    assert (
        subnet is not None
    ), "Unable to find subnet information for provided network name: {}".format(network_name)
    return subnet
