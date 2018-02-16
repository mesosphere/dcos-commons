'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_networks IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging
import shakedown
import sdk_cmd

log = logging.getLogger(__name__)

ENABLE_VIRTUAL_NETWORKS_OPTIONS = {'service': {'virtual_network_enabled': True}}


def check_task_network(task_name, expected_network_name="dcos"):
    """Tests whether a task (and it's parent pod) is on a given network
    """
    _task = shakedown.get_task(task_id=task_name, completed=False)

    assert _task is not None, "Unable to find task named {}".format(task_name)
    if type(_task) == list or type(_task) == tuple:
        assert len(_task) == 1, "Found too many tasks matching {}, got {}"\
            .format(task_name, _task)
        _task = _task[0]

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


def get_and_test_endpoints(package_name, service_name, endpoint_to_get, correct_count):
    """Gets the endpoints for a service or the specified 'endpoint_to_get' similar to running
    $ docs <service> endpoints
    or
    $ dcos <service> endpoints <endpoint_to_get>
    Checks that there is the correct number of endpoints"""
    endpoints = sdk_cmd.svc_cli(package_name, service_name, "endpoints {}".format(endpoint_to_get), json=True)
    assert len(endpoints) == correct_count, "Wrong number of endpoints, got {} should be {}" \
        .format(len(endpoints), correct_count)
    return endpoints


def check_endpoints_on_overlay(endpoints):
    def check_ip_addresses_on_overlay():
        # the overlay IP address should not contain any agent IPs
        return len(set(ip_addresses).intersection(set(shakedown.get_agents()))) == 0

    assert "address" in endpoints, "endpoints: {} missing 'address' key".format(endpoints)
    assert "dns" in endpoints, "endpoints: {} missing 'dns' key".format(endpoints)

    # endpoints should have the format <ip_address>:port
    ip_addresses = [e.split(":")[0] for e in endpoints["address"]]
    assert check_ip_addresses_on_overlay(), \
        "IP addresses for this service should not contain agent IPs, IPs were {}".format(ip_addresses)

    for dns in endpoints["dns"]:
        assert "autoip.dcos.thisdcos.directory" in dns, \
            "DNS {} is incorrect should have autoip.dcos.thisdcos.directory".format(dns)
