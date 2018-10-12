"""
Utilities relating to verifying the metrics functionality as reported
by the DC/OS metrics component.

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_metrics IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import json
import logging
import retrying
import typing

import sdk_cmd
import sdk_tasks

log = logging.getLogger(__name__)


def get_scheduler_metrics(service_name, timeout_seconds=15 * 60):
    """Returns a dict tree of Scheduler metrics fetched directly from the scheduler.
    Returned data will match the content of /service/<svc_name>/v1/metrics.
    """
    return sdk_cmd.service_request("GET", service_name, "/v1/metrics").json()


def get_scheduler_counter(service_name, counter_name, timeout_seconds=15 * 60):
    """Waits for and returns the specified counter value from the scheduler"""

    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def check_for_value():
        try:
            sched_metrics = get_scheduler_metrics(service_name)
            if "counters" not in sched_metrics:
                log.info(
                    "No counters present for service {}. Types were: {}".format(
                        service_name, sched_metrics.keys()
                    )
                )
                return None
            sched_counters = sched_metrics["counters"]
            if counter_name not in sched_counters:
                log.info(
                    "No counter named '{}' was found for service {}. Counters were: {}".format(
                        counter_name, service_name, sched_counters.keys()
                    )
                )
                return None
            value = sched_counters[counter_name]["count"]
            log.info("{} metric counter: {}={}".format(service_name, counter_name, value))
            return value
        except Exception as e:
            log.error("Caught exception trying to get metrics: {}".format(e))
            return None

    return check_for_value()


def wait_for_scheduler_counter_value(
    service_name, counter_name, min_value, timeout_seconds=15 * 60
):
    """Waits for the specified counter value to be reached by the scheduler
    For example, check that `offers.processed` is equal or greater to 1."""

    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def check_for_value():
        value = get_scheduler_counter(service_name, counter_name, timeout_seconds)
        return value >= min_value

    return check_for_value()


def wait_for_metrics_from_cli(task_name: str, timeout_seconds: int) -> typing.Dict:
    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def _getter():
        return get_metrics_from_cli(task_name)

    return _getter()


def get_metrics_from_cli(task_name: str) -> typing.Dict:
    cmd_list = ["task", "metrics", "details", "--json", task_name]
    rc, stdout, stderr = sdk_cmd.run_cli(" ".join(cmd_list))
    if rc:
        log.error("Error fetching metrics for %s:\nSTDOUT=%s\nSTDERR=%s", task_name, stdout, stderr)
        return dict()

    try:
        metrics = json.loads(stdout)
    except json.JSONDecodeError as json_error:
        log.error("Error decoding JSON from %s: %s", stdout, json_error)
        raise

    return metrics


def get_metrics(package_name, service_name, pod_name, task_name):
    """Return a list of DC/OS metrics datapoints.

    Keyword arguments:
    package_name -- the name of the package the service is using
    service_name -- the name of the service to get metrics for
    task_name -- the name of the task whose agent to run metrics commands from
    """

    # Find task entry in mesos state:
    tasks = sdk_tasks.get_service_tasks(service_name)
    for task in tasks:
        if task.name == task_name:
            task_to_check = task
            break
    if task_to_check is None:
        raise Exception(
            "Task named {} not found in service {}: {}".format(task_name, service_name, tasks)
        )

    # Find task's container id via recent TaskStatus:
    rc, stdout, _ = sdk_cmd.svc_cli(
        package_name, service_name, "pod info {}".format(pod_name), print_output=False
    )
    assert rc == 0, "Pod info failed"
    pod_info = json.loads(stdout)
    task_container_id = None
    for task in pod_info:
        if task["info"]["name"] == task_name:
            task_container_id = task["status"]["containerStatus"]["containerId"]["value"]
            break
    if task_container_id is None:
        log.warning("Task named {} not found in pod {}: {}".format(task_name, pod_name, pod_info))
        return []

    # Not related to functionality, but consuming this endpoint to verify metrics integrity
    containers_response = sdk_cmd.cluster_request(
        "GET",
        "/system/v1/agent/{}/metrics/v0/containers".format(task_to_check.agent_id),
        retry=False,
    )
    reported_container_ids = json.loads(containers_response.text)

    container_id_reported = False
    for container_id in reported_container_ids:
        if container_id == task_container_id:
            container_id_reported = True
            break
    if not container_id_reported:
        raise ValueError(
            "The metrics /container endpoint returned {} for agent {}, expected {} to be returned as well".format(
                reported_container_ids, task_to_check.agent_id, task_container_id
            )
        )

    app_response = sdk_cmd.cluster_request(
        "GET",
        "/system/v1/agent/{}/metrics/v0/containers/{}/app".format(
            task_to_check.agent_id, task_container_id
        ),
        retry=False,
    )
    app_response.raise_for_status()
    app_json = app_response.json()

    if "dimensions" not in app_json:
        log.error("Expected key '%s' not found in app metrics: %s", "dimensions", app_json)
        raise Exception("Expected key 'dimensions' not found in app metrics: {}")

    if "task_name" not in app_json["dimensions"]:
        log.error(
            "Expected key '%s' not found in app metrics: %s", "dimensions.task_name", app_json
        )
        raise Exception("Expected key 'dimensions' not found in app metrics: {}")

    if app_json["dimensions"]["task_name"] == task_name:
        return app_json["datapoints"]

    raise Exception("No metrics found for task {} in service {}".format(task_name, service_name))


def check_metrics_presence(emitted_metrics, expected_metrics):
    metrics_exist = True
    for metric in expected_metrics:
        if metric not in emitted_metrics:
            metrics_exist = False
            log.error("Unable to find metric {}".format(metric))
            # don't short-circuit to log if multiple metrics are missing

    if not metrics_exist:
        log.info(
            "Metrics emitted: {},\nMetrics expected: {}".format(emitted_metrics, expected_metrics)
        )

    log.info("Expected metrics exist: {}".format(metrics_exist))
    return metrics_exist


def wait_for_service_metrics(
    package_name, service_name, pod_name, task_name, timeout, expected_metrics_callback
):
    """Checks that the service is emitting the expected values into DC/OS Metrics.
    The assumption is that if the expected metrics are being emitted then so
    are the rest of the metrics.

    Arguments:
    package_name -- the name of the package the service is using
    service_name -- the name of the service to get metrics for
    task_name -- the name of the task whose agent to run metrics commands from
    expected_metrics_callback -- service-specific callback that checks for service-specific metrics
    """

    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout * 1000, retry_on_result=lambda res: not res
    )
    def check_for_service_metrics():
        try:
            log.info(
                "Verifying metrics exist for task {} in service {}".format(task_name, service_name)
            )
            service_metrics = get_metrics(package_name, service_name, pod_name, task_name)
            emitted_metric_names = [metric["name"] for metric in service_metrics]
            return expected_metrics_callback(emitted_metric_names)

        except Exception as e:
            log.error("Caught exception trying to get metrics: {}".format(e))
            return False

    check_for_service_metrics()
