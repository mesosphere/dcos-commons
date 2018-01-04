'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_networks IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import json
import logging
import retrying
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


def get_framework_srv_records(package_name):

    @retrying.retry(wait_exponential_multiplier=1000,
                    wait_exponential_max=120 * 1000)
    def call_shakedown():
        cmd = "curl localhost:8123/v1/enumerate"
        log.info("Running '%s' on master", cmd)
        is_ok, out = shakedown.run_command_on_master(cmd)
        log.info("Running command returned: is_ok=%s\n\tout=%s", is_ok, out)
        assert is_ok, "Failed to get srv records. command was {}".format(cmd)
        try:
            srvs = json.loads(out)
        except Exception as e:
            log.error("Error converting out=%s to json", out)
            log.error(e)
            raise e

        return srvs

    srvs = call_shakedown()
    framework_srvs = [f for f in srvs["frameworks"] if f["name"] == package_name]
    assert len(framework_srvs) == 1, "Got too many srv records matching package {}, got {}"\
        .format(package_name, framework_srvs)
    return framework_srvs[0]


def get_task_record(task_name, fmk_srv_records):
    assert "tasks" in fmk_srv_records, "Framework SRV records missing 'tasks': {}".format(fmk_srv_records)
    task_records = [t for t in fmk_srv_records["tasks"] if t["name"] == task_name]
    assert len(task_records) > 0, "Didn't find task record for {}".format(task_name)
    assert len(task_records) == 1, "Got redundant tasks for {}".format(task_name)
    task_record = task_records[0]
    assert "records" in task_record, "Task record {} missing 'records'".format(task_record)
    return task_record["records"]


def check_port_names(task_info, expected_port_count, expected_port_names):
    assert type(task_info) == dict
    assert "discovery" in task_info
    assert "ports" in task_info["discovery"]
    assert "ports" in task_info["discovery"]["ports"]
    ports_list = task_info["discovery"]["ports"]["ports"]
    assert len(ports_list) == expected_port_count, "Got incorrect number of ports for task {}," \
                                                   "got {} ports, should be {}." \
                                                   .format(task_info, len(ports_list), expected_port_count)
    for port, expected_name in zip(ports_list, expected_port_names):
        assert "name" in port, "port {} missing name".format(port)
        assert port["name"] == expected_name, "Port name wrong, should be {} got {}"\
                                              .format(expected_name, port["name"])


def get_task_srv_records(task_srv_records, prefixes):
    for prefix in prefixes:
        assert len([rec for rec in task_srv_records if prefix in rec["name"]]) > 0, \
            "Didn't find SRV matching prefix {} in {}".format(prefix, task_srv_records)
