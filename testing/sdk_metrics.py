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
    containers_url = "{}/system/v1/agent/{}/metrics/v0/containers".format(
        shakedown.dcos_url(), agent_id)
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


def extract_metric_names(service_name, service_metrics):
    metric_names = [metric["name"] for metric in service_metrics]

    # HDFS metric names need sanitation as they're dynamic.
    # For eg: ip-10-0-0-139.null.rpc.rpc.RpcQueueTimeNumOps
    # This is consistent across all HDFS metric names.
    if "hdfs" in service_name:
        metric_names = ['-'.join(metric_name.split(".")[1:])
                        for metric_name in metric_names]

    return set(metric_names)


def wait_for_service_metrics(package_name, service_name, task_name, timeout, expected_metrics):
    """Checks that the service is emitting the expected metrics.
    The assumption is that if the expected metrics are being emitted then so 
    are the rest of the metrics.

    Arguments:
    package_name -- the name of the package the service is using
    service_name -- the name of the service to get metrics for
    task_name -- the name of the task whose agent to run metrics commands from
    expected_metrics -- a list of metric names to expect the service to emit
    """
    def expected_metrics_exist():
        try:
            log.info("verifying metrics exist for {}".format(service_name))
            service_metrics = get_metrics(
                package_name, service_name, task_name)
            emitted_metric_names = extract_metric_names(
                service_name, service_metrics)
            metrics_exist = True
            for metric in expected_metrics:
                if metric not in emitted_metric_names:
                    metrics_exist = False
                    log.error("Metric {} is not being emitted by {}".format(
                              metric, service_name
                              ))
                    # don't short-circuit to log if multiple metrics are missing

            if not metrics_exist:
                log.info("Metrics emitted: {},\nMetrics expected: {}".format(
                    service_metrics, expected_metrics
                ))
            return metrics_exist
        except Exception as e:
            log.error("Caught exception trying to get metrics: {}".format(e))
            return False

    shakedown.wait_for(expected_metrics_exist, timeout)
