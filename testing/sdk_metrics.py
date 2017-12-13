'''
Utilities relating to verifying the metrics functionality as reported
by the DC/OS metrics component.

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_metrics IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import json
import logging

import shakedown

import sdk_cmd

log = logging.getLogger(__name__)


def get_metrics(package_name, service_name, task_name):
    """Return a list of metrics datapoints.

    Keyword arguments:
    package_name -- the name of the package the service is using
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

    pod_name = '-'.join(task_name.split("-")[:2])
    pod_info = sdk_cmd.svc_cli(package_name, service_name, "pod info {}".format(pod_name), json=True)
    task_info = None
    for task in pod_info:
        if task["info"]["name"] == task_name:
            task_info = task
            break

    if not task_info:
        return []

    task_container_id = task_info["status"]["containerStatus"]["containerId"]["value"]

    # Not related to functionality but consuming this
    # endpoint to verify downstream integrity
    containers_url = "{}/system/v1/agent/{}/metrics/v0/containers".format(shakedown.dcos_url(), agent_id)
    containers_response = sdk_cmd.request("GET", containers_url, retry=False)
    if containers_response.ok is None:
        log.info("Unable to fetch containers list")
        raise Exception(
            "Unable to fetch containers list: {}".format(containers_url))
    reported_container_ids = json.loads(containers_response.text)

    container_id_reported = False
    for container_id in reported_container_ids:
        if container_id == task_container_id:
            container_id_reported = True

    if not container_id_reported:
        raise ValueError("The metrics /container endpoint returned {}, expecting {} to be returned as well".format(
            reported_container_ids, task_container_id))

    app_url = "{}/system/v1/agent/{}/metrics/v0/containers/{}/app".format(
        shakedown.dcos_url(), agent_id, task_container_id)
    app_response = sdk_cmd.request("GET", app_url, retry=False)
    if app_response.ok is None:
        raise ValueError("Failed to get metrics from container")

    app_json = json.loads(app_response.text)
    if app_json['dimensions']['executor_id'] == executor_id:
        return app_json['datapoints']

    raise Exception("No metrics found")


def check_metrics_presence(emitted_metrics, expected_metrics):
    metrics_exist = True
    for metric in expected_metrics:
        if metric not in emitted_metrics:
            metrics_exist = False
            log.error("Unable to find metric {}".format(metric))
            # don't short-circuit to log if multiple metrics are missing

    if not metrics_exist:
        log.info("Metrics emitted: {},\nMetrics expected: {}".format(emitted_metrics, expected_metrics))

    log.info("Expected metrics exist: {}".format(metrics_exist))
    return metrics_exist


def wait_for_service_metrics(package_name, service_name, task_name, timeout, expected_metrics_exist):
    """Checks that the service is emitting the expected metrics.
    The assumption is that if the expected metrics are being emitted then so
    are the rest of the metrics.

    Arguments:
    package_name -- the name of the package the service is using
    service_name -- the name of the service to get metrics for
    task_name -- the name of the task whose agent to run metrics commands from
    expected_metrics_exist -- serivce-specific callback that checks for service-specific metrics
    """
    def check_for_service_metrics():
        try:
            log.info("verifying metrics exist for {}".format(service_name))
            service_metrics = get_metrics(package_name, service_name, task_name)
            emitted_metric_names = [metric["name"] for metric in service_metrics]
            return expected_metrics_exist(emitted_metric_names)

        except Exception as e:
            log.error("Caught exception trying to get metrics: {}".format(e))
            return False

    shakedown.wait_for(check_for_service_metrics, timeout)
