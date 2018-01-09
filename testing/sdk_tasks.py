'''Utilities relating to running commands and HTTP requests

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_tasks IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import json
import logging
import os

import shakedown

import dcos.errors
import retrying

import sdk_cmd
import sdk_plan
import sdk_utils


DEFAULT_TIMEOUT_SECONDS = 30 * 60

log = logging.getLogger(__name__)


def check_running(service_name, expected_task_count, timeout_seconds=DEFAULT_TIMEOUT_SECONDS, allow_more=True):
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: not res)
    def fn():
        try:
            tasks = shakedown.get_service_tasks(service_name)
        except dcos.errors.DCOSHTTPException:
            log.info('Failed to get tasks for service {}'.format(service_name))
            tasks = []
        running_task_names = []
        other_tasks = []
        for t in tasks:
            if t['state'] == 'TASK_RUNNING':
                running_task_names.append(t['name'])
            else:
                other_tasks.append('{}={}'.format(t['name'], t['state']))
        log.info('Waiting for {} running tasks, got {} running/{} total:\n- running: {}\n- other: {}'.format(
            expected_task_count,
            len(running_task_names), len(tasks),
            sorted(running_task_names),
            sorted(other_tasks)))
        if allow_more:
            return len(running_task_names) >= expected_task_count
        else:
            return len(running_task_names) == expected_task_count

    fn()


def get_task_ids(service_name, task_prefix):
    tasks = shakedown.get_service_tasks(service_name)
    matching_tasks = [t for t in tasks if t['name'].startswith(task_prefix)]
    return [t['id'] for t in matching_tasks]


def get_completed_task_id(task_name):
    try:
        tasks = [t['id'] for t in shakedown.get_tasks(completed=True) if t['name'] == task_name]
    except dcos.errors.DCOSHTTPException:
        tasks = []

    return tasks[0] if tasks else None


def check_task_relaunched(task_name, old_task_id, timeout_seconds=DEFAULT_TIMEOUT_SECONDS):
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: not res)
    def fn():
        try:
            task_ids = set([t['id'] for t in shakedown.get_tasks(completed=True) if t['name'] == task_name])
        except dcos.errors.DCOSHTTPException:
            log.info('Failed to get task ids. task_name=%s', task_name)
            task_ids = set([])

        return len(task_ids) > 0 and (old_task_id not in task_ids or len(task_ids) > 1)

    fn()


def check_task_not_relaunched(service_name, task_name, old_task_id, timeout_seconds=DEFAULT_TIMEOUT_SECONDS):
    sdk_plan.wait_for_completed_deployment(service_name)
    sdk_plan.wait_for_completed_recovery(service_name)

    try:
        task_ids = set([t['id'] for t in shakedown.get_tasks() if t['name'] == task_name])
    except dcos.errors.DCOSHTTPException:
        log.info('Failed to get task ids for service {}'.format(service_name))
        task_ids = set([])

    assert len(task_ids) == 1 and old_task_id in task_ids


def check_tasks_updated(service_name, prefix, old_task_ids, timeout_seconds=DEFAULT_TIMEOUT_SECONDS):
    # TODO: strongly consider merging the use of checking that tasks have been replaced (this method)
    # and checking that the deploy/upgrade/repair plan has completed. Each serves a part in the bigger
    # atomic test, that the plan completed properly where properly includes that no old tasks remain.
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: not res)
    def fn():
        try:
            task_ids = get_task_ids(service_name, prefix)
        except dcos.errors.DCOSHTTPException:
            log.info('Failed to get task ids for service {}'.format(service_name))
            task_ids = []

        prefix_clause = ''
        if prefix:
            prefix_clause = ' starting with "{}"'.format(prefix)

        old_set = set(old_task_ids)
        new_set = set(task_ids)
        newly_launched_set = new_set.difference(old_set)
        old_remaining_set = old_set.intersection(new_set)
        # the constraints of old and new task cardinality match should be covered by completion of
        # deploy/recovery/whatever plan, not task cardinality, but some uses of this method are not
        # using the plan, so not the definitive source, so will fail when the finished state of a
        # plan yields more or less tasks per pod.
        all_updated = len(newly_launched_set) == len(new_set) and len(old_remaining_set) == 0 and len(new_set) >= len(old_set)
        if all_updated:
            log.info('All of the tasks{} have updated\n- Old tasks: {}\n- New tasks: {}'.format(
                prefix_clause,
                old_set,
                new_set))
            return all_updated

        # forgive the language a bit, but len('remained') == len('launched'),
        # and similar for the rest of the label for task ids in the log line,
        # so makes for easier reading
        log.info('Waiting for tasks{} to have updated ids:\n- Old tasks (remaining): {}\n- New tasks (launched): {}'.format(
            prefix_clause,
            old_remaining_set,
            newly_launched_set))

    fn()


def check_tasks_not_updated(service_name, prefix, old_task_ids):
    sdk_plan.wait_for_completed_deployment(service_name)
    sdk_plan.wait_for_completed_recovery(service_name)
    task_ids = get_task_ids(service_name, prefix)
    task_sets = "\n- Old tasks: {}\n- Current tasks: {}".format(sorted(old_task_ids), sorted(task_ids))
    log.info('Checking tasks starting with "{}" have not been updated:{}'.format(prefix, task_sets))
    assert set(old_task_ids).issubset(set(task_ids)), 'Tasks starting with "{}" were updated:{}'.format(prefix, task_sets)


def kill_task_with_pattern(pattern, agent_host=None, timeout_seconds=DEFAULT_TIMEOUT_SECONDS):
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: not res)
    def fn():
        command = (
            "sudo kill -9 "
            "$(ps ax | grep {} | grep -v grep | tr -s ' ' | sed 's/^ *//g' | "
            "cut -d ' ' -f 1)".format(pattern))
        if agent_host is None:
            exit_status, _ = shakedown.run_command_on_master(command)
        else:
            exit_status, _ = shakedown.run_command_on_agent(agent_host, command)

        return exit_status

    # might not be able to connect to the agent on first try so we repeat until we can
    fn()


def task_exec(task_name: str, cmd: str, return_stderr_in_stdout: bool = False) -> tuple:
    """
    Invokes the given command on the task via `dcos task exec`.
    :param task_name: Name of task to run command on.
    :param cmd: The command to execute.
    :return: a tuple consisting of the task exec's return code, stdout, and stderr
    """

    if cmd.startswith("./") and sdk_utils.dcos_version_less_than("1.10"):
        full_cmd = os.path.join(get_task_sandbox_path(task_name), cmd)

        if cmd.startswith("./bootstrap"):
            # On 1.9 we beed to set LIB_PROCESS_IP for bootstrap
            full_cmd = "bash -c \"LIBPROCESS_IP=0.0.0.0 {}\"".format(full_cmd)
    else:
        full_cmd = cmd

    exec_cmd = "task exec {task_name} {cmd}".format(task_name=task_name, cmd=full_cmd)
    rc, stdout, stderr = sdk_cmd.run_raw_cli(exec_cmd)

    if return_stderr_in_stdout:
        return rc, stdout + "\n" + stderr

    return rc, stdout, stderr


def get_task_sandbox_path(task_name: str) -> str:
    task_info = get_task_info(task_name)

    if task_info:

        executor_path = task_info["executor_id"]
        if not executor_path:
            executor_path = task_info["id"]
        # Assume the latest run:
        return os.path.join("/var/lib/mesos/slave/slaves", task_info["slave_id"],
                            "frameworks", task_info["framework_id"],
                            "executors", executor_path,
                            "runs/latest")

    return ""


@retrying.retry(stop_max_attempt_number=3, wait_fixed=2000)
def get_task_info(task_name: str) -> dict:
    """
    :return (dict): Get the task information for the specified task
    """
    log.info("Getting task information")
    raw_tasks = sdk_cmd.run_cli("task {task_name} --json".format(task_name=task_name))
    if raw_tasks:
        tasks = json.loads(raw_tasks)
        for task in tasks:
            if task["name"] == task_name:
                log.info("Matched on 'name'")
                return task
            if task.get("id", None) == task_name:
                log.info("Matched on 'id'")
                return task

    log.error("Task %s not found.\nFound: %s", task_name, raw_tasks)
    return {}
