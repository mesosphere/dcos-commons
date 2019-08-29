"""Utilities relating to interaction with service plans

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_plan IN ANY OTHER PARTNER REPOS
************************************************************************
"""

import datetime
import logging
import retrying
from typing import Any, Dict, List, Optional, Union

import sdk_cmd
import sdk_tasks

TIMEOUT_SECONDS = 15 * 60
SHORT_TIMEOUT_SECONDS = 30
MAX_NEW_TASK_FAILURES = 10


class TaskFailuresExceededException(Exception):
    pass


log = logging.getLogger(__name__)


def get_deployment_plan(
    service_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return get_plan(service_name=service_name, plan="deploy", timeout_seconds=timeout_seconds)


def get_recovery_plan(service_name: str, timeout_seconds: int = TIMEOUT_SECONDS) -> Dict[str, Any]:
    return get_plan(service_name=service_name, plan="recovery", timeout_seconds=timeout_seconds)


def get_decommission_plan(
    service_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return get_plan(service_name=service_name, plan="decommission", timeout_seconds=timeout_seconds)


def list_plans(service_name: str, timeout_seconds: int = TIMEOUT_SECONDS) -> List:
    path = "/v1/plans"
    result = sdk_cmd.service_request(
        "GET", service_name, path, timeout_seconds=timeout_seconds
    ).json()
    assert isinstance(result, list)
    return result


def get_plan_once(service_name: str, plan: str) -> Dict[str, Any]:
    path = "/v1/plans/{}".format(plan)
    response = sdk_cmd.service_request("GET", service_name, path, retry=False, raise_on_error=False)
    if (
        response.status_code != 417
    ):  # Plan has errors: Avoid throwing an exception, return plan as-is.
        response.raise_for_status()

    result = response.json()
    assert isinstance(result, dict)
    return result


def get_plan(
    service_name: str, plan: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    @retrying.retry(wait_fixed=1000, stop_max_delay=timeout_seconds * 1000)
    def wait_for_plan() -> Dict[str, Any]:
        return get_plan_once(service_name, plan)

    result = wait_for_plan()
    assert isinstance(result, dict)
    return result


def start_plan(service_name: str, plan: str, parameters: Optional[Dict[str, Any]] = None) -> None:
    sdk_cmd.service_request(
        "POST",
        service_name,
        "/v1/plans/{}/start".format(plan),
        json=parameters if parameters is not None else {},
    )


def force_complete_step(service_name: str, plan: str, phase: str, step: str) -> None:
    sdk_cmd.service_request(
        "POST",
        service_name,
        "/v1/plans/{}/forceComplete?phase={}&step={}".format(plan, phase, step),
    )


def wait_for_completed_recovery(
    service_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return wait_for_completed_plan(service_name, "recovery", timeout_seconds)


def wait_for_in_progress_recovery(
    service_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return wait_for_in_progress_plan(service_name, "recovery", timeout_seconds)


def wait_for_kicked_off_deployment(
    service_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return wait_for_kicked_off_plan(service_name, "deploy", timeout_seconds)


def wait_for_kicked_off_recovery(
    service_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return wait_for_kicked_off_plan(service_name, "recovery", timeout_seconds)


def wait_for_completed_deployment(
    service_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return wait_for_completed_plan(service_name, "deploy", timeout_seconds)


def wait_for_completed_plan(
    service_name: str, plan_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return wait_for_plan_status(service_name, plan_name, "COMPLETE", timeout_seconds)


def wait_for_completed_phase(
    service_name: str, plan_name: str, phase_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return wait_for_phase_status(service_name, plan_name, phase_name, "COMPLETE", timeout_seconds)


def wait_for_completed_step(
    service_name: str,
    plan_name: str,
    phase_name: str,
    step_name: str,
    timeout_seconds: int = TIMEOUT_SECONDS,
) -> Dict[str, Any]:
    return wait_for_step_status(
        service_name, plan_name, phase_name, step_name, "COMPLETE", timeout_seconds
    )


def wait_for_kicked_off_plan(
    service_name: str, plan_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return wait_for_plan_status(
        service_name, plan_name, ["PENDING", "STARTING", "IN_PROGRESS"], timeout_seconds
    )


def wait_for_in_progress_plan(
    service_name: str, plan_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return wait_for_plan_status(service_name, plan_name, "IN_PROGRESS", timeout_seconds)


def wait_for_starting_plan(
    service_name: str, plan_name: str, timeout_seconds: int = TIMEOUT_SECONDS
) -> Dict[str, Any]:
    return wait_for_plan_status(service_name, plan_name, "STARTING", timeout_seconds)


def wait_for_plan_status(
    service_name: str,
    plan_name: str,
    status: Union[List[str], str],
    timeout_seconds: int = TIMEOUT_SECONDS,
) -> Dict[str, Any]:
    """Wait for a plan to have one of the specified statuses"""
    if isinstance(status, str):
        statuses = [status]
    else:
        statuses = status

    initial_failures = sdk_tasks.get_failed_task_count(service_name, retry=True)
    wait_start = datetime.datetime.utcnow()

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds * 1000,
        retry_on_result=lambda res: not res,
        retry_on_exception=lambda e: not isinstance(e, TaskFailuresExceededException),
    )
    def fn() -> Union[Dict[str, Any], bool]:
        failures = sdk_tasks.get_failed_task_count(service_name)
        if failures - initial_failures > MAX_NEW_TASK_FAILURES:
            log.error(
                "Tasks in service %s failed %d times since starting %ds ago to wait for %s to reach %s, aborting.",
                service_name,
                MAX_NEW_TASK_FAILURES,
                (datetime.datetime.utcnow() - wait_start).total_seconds(),
                plan_name,
                statuses,
            )
            raise TaskFailuresExceededException("Service not recoverable: {}".format(service_name))

        plan = get_plan(service_name, plan_name, timeout_seconds=SHORT_TIMEOUT_SECONDS)
        log.info("Waiting for %s %s plan:\n%s", status, plan_name, plan_string(plan_name, plan))
        if plan and plan["status"] in statuses:
            return plan
        else:
            return False

    result = fn()
    assert isinstance(result, dict)
    return result


def wait_for_phase_status(
    service_name: str,
    plan_name: str,
    phase_name: str,
    status: str,
    timeout_seconds: int = TIMEOUT_SECONDS,
) -> Dict[str, Any]:
    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def fn() -> Union[Dict[str, Any], bool]:
        plan = get_plan(service_name, plan_name, SHORT_TIMEOUT_SECONDS)
        phase = get_phase(plan, phase_name)
        log.info(
            "Waiting for {} {}.{} phase:\n{}".format(
                status, plan_name, phase_name, plan_string(plan_name, plan)
            )
        )
        if phase and phase["status"] == status:
            return plan
        else:
            return False

    result = fn()
    assert isinstance(result, dict)
    return result


def wait_for_step_status(
    service_name: str,
    plan_name: str,
    phase_name: str,
    step_name: str,
    status: str,
    timeout_seconds: int = TIMEOUT_SECONDS,
) -> Dict[str, Any]:
    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def fn() -> Union[Dict[str, Any], bool]:
        plan = get_plan(service_name, plan_name, SHORT_TIMEOUT_SECONDS)
        step = get_step(get_phase(plan, phase_name), step_name)
        log.info(
            "Waiting for {} {}.{}.{} step:\n{}".format(
                status, plan_name, phase_name, step_name, plan_string(plan_name, plan)
            )
        )
        if step and step["status"] == status:
            return plan
        else:
            return False

    result = fn()
    assert isinstance(result, dict)
    return result


def recovery_plan_is_empty(service_name: str) -> bool:
    plan = get_recovery_plan(service_name)
    return len(plan["phases"]) == 0 and len(plan["errors"]) == 0 and plan["status"] == "COMPLETE"


def get_phase(plan: Dict[str, Any], name: str) -> Any:
    return get_child(plan, "phases", name)


def get_step(phase: Dict[str, Any], name: str) -> Any:
    return get_child(phase, "steps", name)


def get_all_step_names(plan: Dict[str, Any]) -> List[str]:
    steps: List[str] = []
    for phase in plan["phases"]:
        steps += [step["name"] for step in phase["steps"]]
    return steps


def get_child(parent: Dict[str, Any], children_field: str, name: str) -> Any:
    if parent is None:
        return None
    for child in parent[children_field]:
        if child["name"] == name:
            return child
    return None


def plan_string(plan_name: str, plan: Dict[str, Any]) -> str:
    if plan is None:
        return "{}=NULL!".format(plan_name)

    def phase_string(phase: Dict[str, Any]) -> str:
        """ Formats the phase output as follows:

        deploy STARTING:
        - node-deploy STARTING: node-0:[server] = STARTING, node-1:[server] = PENDING, node-2:[server] = PENDING
        - node-other PENDING: somestep=PENDING
        - errors: foo, bar
        """
        return "\n- {} ({}): {}".format(
            phase["name"],
            phase["status"],
            ", ".join("{}={}".format(step["name"], step["status"]) for step in phase["steps"]),
        )

    plan_str = "{} ({}):{}".format(
        plan_name, plan["status"], "".join(phase_string(phase) for phase in plan["phases"])
    )
    if plan.get("errors", []):
        plan_str += "\n- errors: {}".format(", ".join(plan["errors"]))
    return plan_str
