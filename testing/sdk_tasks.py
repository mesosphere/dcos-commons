'''Utilities relating to running commands and HTTP requests'''

import dcos.errors
import sdk_cmd
import sdk_spin
import shakedown


def check_running(service_name, expected_task_count):
    def fn():
        try:
            tasks = shakedown.get_service_tasks(service_name)
        except dcos.errors.DCOSHTTPException:
            print('Failed to get tasks for service {}'.format(service_name))
            tasks = []
        running_task_names = []
        other_tasks = []
        for t in tasks:
            if t['state'] == 'TASK_RUNNING':
                running_task_names.append(t['name'])
            else:
                other_tasks.append('{}={}'.format(t['name'], t['state']))
        print('Waiting for {} running tasks, got {} running/{} total:\n- running: {}\n- other: {}'.format(
            expected_task_count,
            len(running_task_names), len(tasks),
            running_task_names,
            other_tasks))
        return len(running_task_names) >= expected_task_count
    sdk_spin.time_wait_noisy(lambda: fn())


def get_task_ids(service_name, task_prefix):
    tasks = shakedown.get_service_tasks(service_name)
    matching_tasks = [t for t in tasks if t['name'].startswith(task_prefix)]
    return [t['id'] for t in matching_tasks]


def check_tasks_updated(service_name, prefix, old_task_ids):
    def fn():
        try:
            task_ids = get_task_ids(service_name, prefix)
        except dcos.errors.DCOSHTTPException:
            print('Failed to get task ids for service {}'.format(service_name))
            task_ids = []

        print('Waiting for tasks starting with "{}" to be updated:\n- Old tasks: {}\n- Current tasks: {}'.format(
            prefix, old_task_ids, task_ids))
        all_updated = True
        for id in task_ids:
            if id in old_task_ids:
                all_updated = False
        if len(task_ids) < len(old_task_ids):
            all_updated = False
        return all_updated

    sdk_spin.time_wait_noisy(lambda: fn())


def check_tasks_not_updated(service_name, prefix, old_task_ids):
    def fn():
        try:
            task_ids = get_task_ids(service_name, prefix)
        except dcos.errors.DCOSHTTPException:
            print('Failed to get task ids for service {}'.format(service_name))
            task_ids = []

        print('Checking prior tasks starting with "{}" are undisturbed:\n- Old tasks: {}\n- Current tasks: {}'.format(
            prefix, old_task_ids, task_ids))
        for task_id in task_ids:
            if task_id not in old_task_ids:
                return False
        return True

    try:
        sdk_spin.time_wait_noisy(lambda: fn(), timeout_seconds=60)
    except shakedown.TimeoutExpired:
        print('Timeout reached as expected')


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
