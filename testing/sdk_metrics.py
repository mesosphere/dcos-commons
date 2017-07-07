'''
Utilities relating to verifying the metrics functionality as reported
by the DC/OS metrics component.
'''

import shakedown

import sdk_hosts as hosts
import sdk_utils as utils
import json
import ast

def get_metrics(service_name, task_name):
    """Return a list of metrics datapoints.

    Keyword arguments:
    service_name -- the name of the service to get metrics for
    task_name -- the name of the task whose agent to run metrics commands from
    """
    host = hosts.system_host(service_name, task_name)
    auth_token, _, _ = shakedown.run_dcos_command('config show core.dcos_acs_token')
    auth_token = auth_token.strip()

    service_containers_cmd = """curl --header "Authorization: token={}"
        -s http://localhost:61001/system/v1/metrics/v0/containers""".format(auth_token).replace("\n", "")
    _, output = shakedown.run_command_on_agent(host, service_containers_cmd)
    # We need at least one container whose metrics we can return
    if output == "[]":
        return []

    # Sanitize output as it's a string-represented list i.e. '["bc005e73...","2ef32c62..."]'
    containers = ast.literal_eval(output)
    # Need just one container to probe so just get the first one
    container_id = containers[0]
    metrics_cmd = """curl --header "Authorization: token={}"
        -s http://localhost:61001/system/v1/metrics/v0/containers/{}/app""".format(auth_token, container_id).replace("\n","")
    _, output = shakedown.run_command_on_agent(host, metrics_cmd)

    metrics = json.loads(output)
    return metrics["datapoints"]

def wait_for_any_metrics(service_name, task_name, timeout):
    def metrics_exist():
        utils.out("verifying metrics exist for {}".format(service_name))
        service_metrics = get_metrics(service_name, task_name)
        return len(service_metrics) != 0

    shakedown.wait_for(metrics_exist, timeout)
