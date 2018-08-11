"""
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_networks IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import logging

import sdk_agents
import sdk_cmd
import sdk_tasks

log = logging.getLogger(__name__)

ENABLE_VIRTUAL_NETWORKS_OPTIONS = {"service": {"virtual_network_enabled": True}}


def check_task_network(task_name, expected_network_name="dcos"):
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


def get_and_test_endpoints(package_name, service_name, endpoint_to_get, correct_count):
    """Gets the endpoints for a service or the specified 'endpoint_to_get' similar to running
    $ docs <service> endpoints
    or
    $ dcos <service> endpoints <endpoint_to_get>
    Checks that there is the correct number of endpoints"""
    endpoints = sdk_cmd.svc_cli(
        package_name, service_name, "endpoints {}".format(endpoint_to_get), json=True
    )
    assert len(endpoints) == correct_count, "Wrong number of endpoints, got {} should be {}".format(
        len(endpoints), correct_count
    )
    return endpoints


def check_endpoints_on_overlay(endpoints):
    def check_ip_addresses_on_overlay():
        # the overlay IP address should not contain any agent IPs
        all_agent_ips = set([agent["hostname"] for agent in sdk_agents.get_agents()])
        return len(set(ip_addresses).intersection(all_agent_ips)) == 0

    assert "address" in endpoints, "endpoints: {} missing 'address' key".format(endpoints)
    assert "dns" in endpoints, "endpoints: {} missing 'dns' key".format(endpoints)

    # endpoints should have the format <ip_address>:port
    ip_addresses = [e.split(":")[0] for e in endpoints["address"]]
    assert (
        check_ip_addresses_on_overlay()
    ), "IP addresses for this service should not contain agent IPs, IPs were {}".format(
        ip_addresses
    )

    for dns in endpoints["dns"]:
        assert (
            "autoip.dcos.thisdcos.directory" in dns
        ), "DNS {} is incorrect should have autoip.dcos.thisdcos.directory".format(
            dns
        )
