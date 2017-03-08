'''Utilities relating to running commands and HTTP requests'''

import dcos.errors
import shakedown

DEFAULT_DEPLOY_TIMEOUT = 15 * 60


def _prefix_cb(task_prefix):
    def fn(task):
        return task['name'].startswith(task_prefix)
    return fn


def get_task_ids(service_name, task_prefix):
    return shakedown.get_service_task_ids(service_name, _prefix_cb(task_prefix))


def check_tasks_updated(service_name, task_prefix, old_task_ids, timeout_sec=DEFAULT_DEPLOY_TIMEOUT):
    shakedown.wait_for_service_tasks_all_changed(
        service_name, old_task_ids, _prefix_cb(task_prefix), timeout_sec=timeout_sec)


def check_tasks_not_updated(service_name, task_prefix, old_task_ids):
    shakedown.wait_for_service_tasks_all_unchanged(
        service_name, old_task_ids, _prefix_cb(task_prefix))


def kill_task_with_pattern(pattern, host=None):
    command = (
        "sudo kill -9 "
        "$(ps ax | grep {} | grep -v grep | tr -s ' ' | sed 's/^ *//g' | "
        "cut -d ' ' -f 1)".format(pattern))
    if host is None:
        result = shakedown.run_command_on_master(command)
    else:
        result = shakedown.run_command_on_agent(host, command)

    if not result:
        raise RuntimeError('Failed to kill task with pattern "{}"'.format(pattern))
