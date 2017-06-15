import json

import shakedown


def check_task_network(task_name, expected_network_name="dcos"):
    """Tests whether a task (and it's parent pod) is on a given network
    """
    _task = shakedown.get_task(task_id=task_name, completed=False)
    for status in _task["statuses"]:
        if status["state"] == "TASK_RUNNING":
            for network_info in status["container_status"]["network_infos"]:
                if expected_network_name is not None:
                    assert "name" in network_info, \
                        "Didn't find network name in NetworkInfo for task {task} with " \
                        "status:{status}".format(task=task_name, status=status)
                    assert network_info["name"] == expected_network_name, \
                        "Expected network name:{expected} found:{observed}" \
                        .format(expected=expected_network_name, observed=network_info["name"])
                else:
                    assert "name" not in network_info, \
                        "Task {task} has network name when it shouldn't has status:{status}" \
                        .format(task=task_name, status=status)


def get_endpoints(endpoint_to_get, package_name, correct_count):
    endpoints, _, rc = shakedown.run_dcos_command("{} endpoints {}".format(package_name, endpoint_to_get))
    assert rc == 0, "Failed to get endpoints on overlay network"
    endpoints = json.loads(endpoints)
    assert len(endpoints) == correct_count, "Wrong number of endpoints, got {} should be {}" \
        .format(len(endpoints), correct_count)
    return endpoints


def check_endpoints_on_overlay(endpoints):
    ip_addresses = [e.split(":")[0] for e in endpoints["address"]]
    assert len(set(ip_addresses).intersection(set(shakedown.get_agents()))) == 0, \
        "IP addresses for this service should not contain agent IPs, IPs were {}".format(ip_addresses)

    assert "dns" in endpoints, "Endpoints missing DNS key"
    for dns in endpoints["dns"]:
        assert "autoip.dcos.thisdcos.directory" in dns, "DNS {} is incorrect should have autoip.dcos.thisdcos." \
                                                        "directory".format(dns)
