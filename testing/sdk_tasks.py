#!/usr/bin/python

import shakedown

# Utilities relating to state of service tasks

def check_health(service_name, expected_task_count):
    def fn():
        try:
            tasks = shakedown.get_service_tasks(service_name)
        except dcos.errors.DCOSHTTPException:
            tasks = []
        running_tasks = [t for t in tasks if t['state'] == 'TASK_RUNNING']
        print('Waiting for {} healthy tasks, got {}/{}'.format(
            expected_task_count, len(running_tasks), len(tasks)))
        return len(running_tasks) >= expected_task_count

    assert shakedown.time_wait(lambda: fn(), timeout_seconds=15 * 60)


def get_task_ids(service_name, task_prefix):
    tasks = shakedown.get_service_tasks(service_name)
    matching_tasks = [t for t in tasks if t['name'].startswith(task_prefix)]
    return [t['id'] for t in matching_tasks]


def check_tasks_updated(prefix, old_task_ids):
    assert _get_tasks_updated(prefix, old_task_ids)


def check_tasks_not_updated(prefix, old_task_ids):
    assert not _get_tasks_updated(prefix, old_task_ids)


def _get_tasks_updated(prefix, old_task_ids):
    def fn():
        try:
            task_ids = get_task_ids(prefix)
        except dcos.errors.DCOSHTTPException:
            task_ids = []
        print('Old task ids: ' + str(old_task_ids))
        print('New task ids: ' + str(task_ids))
        all_updated = True
        for id in task_ids:
            if id in old_task_ids:
                all_updated = False
        if not len(task_ids) >= len(old_task_ids):
            all_updated = False
        return success

    return shakedown.time_wait(lambda: fn(), timeout_seconds=15 * 60)


def kill_task_with_pattern(pattern, host=None):
    command = (
        "sudo kill -9 "
        "$(ps ax | grep {} | grep -v grep | tr -s ' ' | sed 's/^ *//g' | "
        "cut -d ' ' -f 1)".format(pattern)
    )
    if host is None:
        result = shakedown.run_command_on_master(command)
    else:
        result = shakedown.run_command_on_agent(host, command)

    if not result:
        raise RuntimeError('Failed to kill task with pattern "{}"'.format(pattern))
