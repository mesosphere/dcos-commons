'''Utilities relating to running commands and HTTP requests

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_tasks IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging

import dcos.errors
import retrying
import sdk_plan
import shakedown

DEFAULT_TIMEOUT_SECONDS = 30 * 60

log = logging.getLogger(__name__)


def check_running(service_name, expected_task_count, timeout_seconds=DEFAULT_TIMEOUT_SECONDS):
    @retrying.retry(
        wait_fixed=5000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: res is False)
    def wait_for_running():
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
        return len(running_task_names) >= expected_task_count

    wait_for_running()


def get_task_ids(service_name, task_prefix):
    tasks = shakedown.get_service_tasks(service_name)
    matching_tasks = [t for t in tasks if t['name'].startswith(task_prefix)]
    return [t['id'] for t in matching_tasks]


def check_tasks_updated(service_name, prefix, old_task_ids, timeout_seconds=DEFAULT_TIMEOUT_SECONDS):
    # TODO: strongly consider merging the use of checking that tasks have been replaced (this method)
    # and checking that the deploy/upgrade/repair plan has completed. Each serves a part in the bigger
    # atomic test, that the plan completed properly where properly includes that no old tasks remain.
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: res is False)
    def wait_for_update():
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
        # the constrainst of old and new task cardinality match should be covered by completion of
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
        log.info('Waiting for tasks{} to have updated ids:\n- Old tasks (remained): {}\n- New tasks (launched): {}'.format(
            prefix_clause,
            old_remaining_set,
            newly_launched_set))

    wait_for_update()


def check_tasks_not_updated(service_name, prefix, old_task_ids):
    sdk_plan.wait_for_completed_deployment(service_name)
    sdk_plan.wait_for_completed_recovery(service_name)
    task_ids = get_task_ids(service_name, prefix)
    task_sets = "\n- Old tasks: {}\n- Current tasks: {}".format(sorted(old_task_ids), sorted(task_ids))
    log.info('Checking tasks starting with "{}" have not been updated:{}'.format(prefix, task_sets))
    assert set(old_task_ids).issubset(set(task_ids)), "Tasks got updated:{}".format(task_sets)


def kill_task_with_pattern(pattern, agent_host=None, timeout_seconds=DEFAULT_TIMEOUT_SECONDS):

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: res is False)
    def retry_kill():
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
    retry_kill()
