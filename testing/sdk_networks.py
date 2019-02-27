"""
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_networks IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import json as jsonlib
import logging
import retrying
from typing import Dict, Union

import sdk_agents
import sdk_cmd
import sdk_tasks


log = logging.getLogger(__name__)


ENABLE_VIRTUAL_NETWORKS_OPTIONS = {"service": {"virtual_network_enabled": True}}


@retrying.retry(wait_fixed=1000, stop_max_delay=5 * 1000, retry_on_result=lambda res: not res)
def _wait_for_endpoint_info(
    package_name: str, service_name: str, endpoint_name: str, json: bool
) -> Union[Dict, str]:

    cmd = " ".join(part for part in ["endpoints", endpoint_name] if part)
    rc, stdout, _ = sdk_cmd.svc_cli(package_name, service_name, cmd)
    assert rc == 0, "Failed to get endpoint named {}".format(endpoint_name)

    if json:
        return jsonlib.loads(stdout)
    return stdout


def get_endpoint_names(package_name: str, service_name: str) -> list:
    """Returns a list of endpoint names for the specified service."""
    return _wait_for_endpoint_info(package_name, service_name, None, json=True)


def get_endpoint(package_name: str, service_name: str, endpoint_name: str) -> typing.Dict:
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
    assert endpoint_name, "Missing endpoint_name. To get list of endpoint names, use get_endpoint_names()."

    return _wait_for_endpoint_info(package_name, service_name, endpoint_name, True)


def get_endpoint_string(package_name: str, service_name: str, endpoint_name: str) -> str:
    """Returns the content of the specified custom endpoint definition as a string.
    """
    # Catch if an empty string is passed in. Technically the command would succeed and return a list of endpoint names,
    # but they should use get_endpoint_names() for this.
    assert endpoint_name, "Missing endpoint_name. To get list of endpoint names, use get_endpoint_names()."

    info = _wait_for_endpoint_info(package_name, service_name, endpoint_name, False)
    return info.strip()


def check_task_network(task_name: str, expected_network_name: str = "dcos") -> None:
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


def check_endpoint_on_overlay(package_name: str, service_name: str, endpoint_to_get: str, expected_task_count: int) -> None:
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
    assert len(ip_addresses.intersection(all_agent_ips)) == 0, "Overlay IPs should not match any agent IPs"

    for dns in endpoint_dns:
        assert "autoip.dcos.thisdcos.directory" in dns, "Expected 'autoip.dcos.thisdcos.directory' in DNS entry"
