'''
Utilities relating to verifying the metrics functionality as reported
by the DC/OS metrics component.
'''
import json
import logging

import shakedown

import sdk_cmd as cmd

log = logging.getLogger(__name__)


def get_metrics(service_name, task_name):
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

    # Fetch the list of containers for the agent
    containers_url = "{}/system/v1/agent/{}/metrics/v0/containers".format(shakedown.dcos_url(), agent_id)
    containers_response = cmd.request("GET", containers_url, retry=False)
    if containers_response.ok is None:
        log.info("Unable to fetch containers list")
        raise Exception("Unable to fetch containers list: {}".format(containers_url))

    for container in json.loads(containers_response.text):
        app_url = "{}/system/v1/agent/{}/metrics/v0/containers/{}/app".format(shakedown.dcos_url(), agent_id, container)
        app_response = cmd.request("GET", app_url, retry=False)
        if app_response.ok is None:
            continue

        app_json = json.loads(app_response.text)
        if app_json['dimensions']['executor_id'] == executor_id:
            return app_json['datapoints']

    raise Exception("No metrics found")


def wait_for_any_metrics(service_name, task_name, timeout):
    def metrics_exist():
        log.info("verifying metrics exist for {}".format(service_name))
        service_metrics = get_metrics(service_name, task_name)
        return len(service_metrics) != 0

    shakedown.wait_for(metrics_exist, timeout)
