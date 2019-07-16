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
from typing import Any, Callable, Dict, List, Optional, Union

import sdk_cmd
import sdk_tasks

log = logging.getLogger(__name__)


def get_scheduler_metrics(service_name: str, timeout_seconds: int = 15 * 60) -> Dict[str, Any]:
    """Returns a dict tree of Scheduler metrics fetched directly from the scheduler.
    Returned data will match the content of /service/<svc_name>/v1/metrics.
    """
    response = sdk_cmd.service_request("GET", service_name, "/v1/metrics")
    response_json = response.json()
    assert isinstance(response_json, dict)
    return response_json


def get_scheduler_counter(
    service_name: str, counter_name: str, timeout_seconds: int = 15 * 60
) -> int:
    """Waits for and returns the specified counter value from the scheduler"""

    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def check_for_value() -> Optional[int]:
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
            assert isinstance(value, int)
            log.info("{} metric counter: {}={}".format(service_name, counter_name, value))
            return value
        except Exception as e:
            log.error("Caught exception trying to get metrics: {}".format(e))
            return None

    return int(check_for_value())


def wait_for_scheduler_counter_value(
    service_name: str, counter_name: str, min_value: int, timeout_seconds: int = 15 * 60
) -> bool:
    """Waits for the specified counter value to be reached by the scheduler
    For example, check that `offers.processed` is equal or greater to 1."""

    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def check_for_value() -> bool:
        value = get_scheduler_counter(service_name, counter_name, timeout_seconds)
        return value >= min_value

    return bool(check_for_value())


def get_scheduler_gauge(service_name: str, gauge_name: str, timeout_seconds: int = 15 * 60) -> Any:
    """Waits for and returns the specified gauge value from the scheduler"""

    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def check_for_value() -> Optional[Any]:
        try:
            sched_metrics = get_scheduler_metrics(service_name)
            if "gauges" not in sched_metrics:
                log.info(
                    "No gauges present for service {}. Types were: {}".format(
                        service_name, sched_metrics.keys()
                    )
                )
                return None
            sched_gauges = sched_metrics["gauges"]
            if gauge_name not in sched_gauges:
                log.info(
                    "No gauge named '{}' was found for service {}. Gauges were: {}".format(
                        gauge_name, service_name, sched_gauges.keys()
                    )
                )
                return None
            value = sched_gauges[gauge_name]["value"]
            log.info("{} metric gauge: {}={}".format(service_name, gauge_name, value))
            return value
        except Exception as e:
            log.error("Caught exception trying to get metrics: {}".format(e))
            return None

    return check_for_value()


def wait_for_scheduler_gauge_value(
    service_name: str, gauge_name: str, condition: lambda res: bool, timeout_seconds: int = 15 * 60
) -> bool:
    """Waits for the specified gauge value to be reached by the scheduler
    For example, check that `is_suppressed` is set to true."""

    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def check_for_value() -> bool:
        return condition(get_scheduler_gauge(service_name, gauge_name, timeout_seconds))

    return check_for_value()


def wait_for_metrics_from_cli(task_name: str, timeout_seconds: int) -> List[Dict[str, Any]]:
    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def _getter() -> Union[Dict[str, Any], List[Dict[str, Any]]]:
        return get_metrics_from_cli(task_name)

    return list(_getter())


def get_metrics_from_cli(task_name: str) -> Union[Dict[str, Any], List[Dict[str, Any]]]:
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

    return list(metrics)


def get_metrics(package_name: str, service_name: str, pod_name: str, task_name: str) -> List:
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
        raise Exception("Expected key 'dimensions' not found in app metrics")

    if "task_name" not in app_json["dimensions"]:
        log.error(
            "Expected key '%s' not found in app metrics: %s", "dimensions.task_name", app_json
        )
        raise Exception("Expected key 'dimensions.task_name' not found in app metrics")

    if app_json["dimensions"]["task_name"] == task_name:
        return list(app_json["datapoints"])

    raise Exception("No metrics found for task {} in service {}".format(task_name, service_name))


def check_metrics_presence(emitted_metrics: List[str], expected_metrics: List[str]) -> bool:
    """Check whether a given list contains all
    """
    lower_case_emitted_metrics = set(map(lambda m: m.lower(), emitted_metrics))

    missing_metrics = []
    for metric in expected_metrics:
        if metric.lower() not in lower_case_emitted_metrics:
            missing_metrics.append(metric)

    if missing_metrics:
        log.warning("Expected metrics: %s", expected_metrics)
        log.warning("Emitted metrics: %s", emitted_metrics)
        log.warning("The following metrics are missing: %s", missing_metrics)
        return False

    return True


def wait_for_service_metrics(
    package_name: str,
    service_name: str,
    pod_name: str,
    task_name: str,
    timeout: int,
    expected_metrics_callback: Callable,
) -> Any:
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
    def check_for_service_metrics() -> bool:
        try:
            log.info(
                "Verifying metrics exist for task {} in service {}".format(task_name, service_name)
            )
            service_metrics = get_metrics(package_name, service_name, pod_name, task_name)
            emitted_metric_names = [metric["name"] for metric in service_metrics]
            return bool(expected_metrics_callback(emitted_metric_names))

        except Exception as e:
            log.error("Caught exception trying to get metrics: {}".format(e))
            return False

    check_for_service_metrics()
