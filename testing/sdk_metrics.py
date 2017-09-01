'''
Utilities relating to verifying the metrics functionality as reported
by the DC/OS metrics component.
'''
import json
import logging

import shakedown

import sdk_cmd

log = logging.getLogger(__name__)


def get_metrics(package_name, service_name, task_name):
    """Return a list of metrics datapoints.

    Keyword arguments:
    service_name -- the name of the service to get metrics for
    task_name -- the name of the task whose agent to run metrics commands from
    """
    tasks = shakedown.get_service_tasks(service_name)
    for task in tasks:
        if task['name'] == task_name:
            task_to_check = task

    if task_to_check is None:
        raise Exception("Could not find task")

    agent_id = task_to_check['slave_id']
    executor_id = task_to_check['executor_id']

    # TODO: uncomment the following block of comments when the /containers endpoint reports the correct container IDs
    # and remove the code following the comments that gets the correct container ID via 'pod info'
    ## Fetch the list of containers for the agent
    #containers_url = "{}/system/v1/agent/{}/metrics/v0/containers".format(shakedown.dcos_url(), agent_id)
    #containers_response = sdk_cmd.request("GET", containers_url, retry=False)
    #if containers_response.ok is None:
    #    log.info("Unable to fetch containers list")
    #    raise Exception("Unable to fetch containers list: {}".format(containers_url))

    # instead of receiving the pod name in this function's parameter list, extract
    # the name of the pod from the task name to not break the code when the
    # above comment-block is uncommented
    pod_name = '-'.join(task_name.split("-")[:2])
    pod_info = sdk_cmd.svc_cli(package_name, service_name, "pod info {}".format(pod_name), json=True)
    task_info = None
    for task in pod_info:
        if task["info"]["name"] == task_name:
            task_info = task
            break

    if not task_info:
        return []

    container_id = task_info["status"]["containerStatus"]["containerId"]["value"]

    #for container_id in json.loads(containers_response.text):
    app_url = "{}/system/v1/agent/{}/metrics/v0/containers/{}/app".format(
        shakedown.dcos_url(), agent_id, container_id)
    app_response = sdk_cmd.request("GET", app_url, retry=False)
    if app_response.ok is None:
        raise("Failed to get metrics from container")
        #continue

    app_json = json.loads(app_response.text)
    if app_json['dimensions']['executor_id'] == executor_id:
        return app_json['datapoints']

    raise Exception("No metrics found")


def wait_for_any_metrics(package_name, service_name, task_name, timeout):
    def metrics_exist():
        log.info("verifying metrics exist for {}".format(service_name))
        service_metrics = get_metrics(package_name, service_name, task_name)
        # there are 2 generic metrics that are always emitted
        return len(service_metrics) > 2

    shakedown.wait_for(metrics_exist, timeout)
