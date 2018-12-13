"""Utilities relating to interaction with service plans

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_plan IN ANY OTHER PARTNER REPOS
************************************************************************
"""

import datetime
import logging
import retrying

import sdk_cmd
import sdk_tasks

TIMEOUT_SECONDS = 15 * 60
SHORT_TIMEOUT_SECONDS = 30
MAX_NEW_TASK_FAILURES = 10


class TaskFailuresExceededException(Exception):
    pass


log = logging.getLogger(__name__)


def get_deployment_plan(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return get_plan(service_name, "deploy", timeout_seconds)


def get_recovery_plan(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return get_plan(service_name, "recovery", timeout_seconds)


def get_decommission_plan(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return get_plan(service_name, "decommission", timeout_seconds)


def list_plans(service_name, timeout_seconds=TIMEOUT_SECONDS, multiservice_name=None):
    if multiservice_name is None:
        path = "/v1/plans"
    else:
        path = "/v1/service/{}/plans".format(multiservice_name)
    return sdk_cmd.service_request(
        "GET", service_name, path, timeout_seconds=timeout_seconds
    ).json()


def get_plan_once(service_name, plan, multiservice_name=None):
    if multiservice_name is None:
        path = "/v1/plans/{}".format(plan)
    else:
        path = "/v1/service/{}/plans/{}".format(multiservice_name, plan)

    response = sdk_cmd.service_request("GET", service_name, path, retry=False, raise_on_error=False)
    if response.status_code == 417:
        return response  # Plan has errors: Avoid throwing an exception, return plan as-is.
    response.raise_for_status()
    return response.json()


def get_plan(service_name, plan, timeout_seconds=TIMEOUT_SECONDS, multiservice_name=None):
    @retrying.retry(wait_fixed=1000, stop_max_delay=timeout_seconds * 1000)
    def wait_for_plan():
        return get_plan_once(service_name, plan, multiservice_name)

    return wait_for_plan()


def start_plan(service_name, plan, parameters=None):
    sdk_cmd.service_request(
        "POST",
        service_name,
        "/v1/plans/{}/start".format(plan),
        json=parameters if parameters is not None else {},
    )


def wait_for_completed_recovery(
    service_name, timeout_seconds=TIMEOUT_SECONDS, multiservice_name=None
):
    return wait_for_completed_plan(service_name, "recovery", timeout_seconds, multiservice_name)


def wait_for_in_progress_recovery(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_in_progress_plan(service_name, "recovery", timeout_seconds)


def wait_for_kicked_off_deployment(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_kicked_off_plan(service_name, "deploy", timeout_seconds)


def wait_for_kicked_off_recovery(service_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_kicked_off_plan(service_name, "recovery", timeout_seconds)


def wait_for_completed_deployment(
    service_name, timeout_seconds=TIMEOUT_SECONDS, multiservice_name=None
):
    return wait_for_completed_plan(service_name, "deploy", timeout_seconds, multiservice_name)


def wait_for_completed_plan(
    service_name, plan_name, timeout_seconds=TIMEOUT_SECONDS, multiservice_name=None
):
    return wait_for_plan_status(
        service_name, plan_name, "COMPLETE", timeout_seconds, multiservice_name
    )


def wait_for_completed_phase(service_name, plan_name, phase_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_phase_status(service_name, plan_name, phase_name, "COMPLETE", timeout_seconds)


def wait_for_completed_step(
    service_name, plan_name, phase_name, step_name, timeout_seconds=TIMEOUT_SECONDS
):
    return wait_for_step_status(
        service_name, plan_name, phase_name, step_name, "COMPLETE", timeout_seconds
    )


def wait_for_kicked_off_plan(service_name, plan_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_plan_status(
        service_name, plan_name, ["PENDING", "STARTING", "IN_PROGRESS"], timeout_seconds
    )


def wait_for_in_progress_plan(service_name, plan_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_plan_status(service_name, plan_name, "IN_PROGRESS", timeout_seconds)


def wait_for_starting_plan(service_name, plan_name, timeout_seconds=TIMEOUT_SECONDS):
    return wait_for_plan_status(service_name, plan_name, "STARTING", timeout_seconds)


def wait_for_plan_status(
    service_name, plan_name, status, timeout_seconds=TIMEOUT_SECONDS, multiservice_name=None
):
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
    def fn():
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

        plan = get_plan(
            service_name,
            plan_name,
            timeout_seconds=SHORT_TIMEOUT_SECONDS,
            multiservice_name=multiservice_name,
        )
        log.info("Waiting for %s %s plan:\n%s", status, plan_name, plan_string(plan_name, plan))
        if plan and plan["status"] in statuses:
            return plan
        else:
            return False

    return fn()


def wait_for_phase_status(
    service_name, plan_name, phase_name, status, timeout_seconds=TIMEOUT_SECONDS
):
    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def fn():
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

    return fn()


def wait_for_step_status(
    service_name, plan_name, phase_name, step_name, status, timeout_seconds=TIMEOUT_SECONDS
):
    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def fn():
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

    return fn()


def recovery_plan_is_empty(service_name):
    plan = get_recovery_plan(service_name)
    return len(plan["phases"]) == 0 and len(plan["errors"]) == 0 and plan["status"] == "COMPLETE"


def get_phase(plan, name):
    return get_child(plan, "phases", name)


def get_step(phase, name):
    return get_child(phase, "steps", name)


def get_all_step_names(plan):
    steps = []
    for phase in plan["phases"]:
        steps += [step["name"] for step in phase["steps"]]
    return steps


def get_child(parent, children_field, name):
    if parent is None:
        return None
    for child in parent[children_field]:
        if child["name"] == name:
            return child
    return None


def plan_string(plan_name, plan):
    if plan is None:
        return "{}=NULL!".format(plan_name)

    def phase_string(phase):
        """ Formats the phase output as follows:

        deploy STARTING:
        - node-deploy STARTING: node-0:[server]=STARTING, node-1:[server]=PENDING, node-2:[server]=PENDING
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
