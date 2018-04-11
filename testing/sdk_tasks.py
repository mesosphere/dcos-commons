'''Utilities relating to running commands and HTTP requests

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_tasks IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging
import retrying

import shakedown
import dcos.errors
import sdk_cmd
import sdk_package_registry
import sdk_plan


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


class Task(object):
    '''Entry value returned by get_summary()'''

    @staticmethod
    def parse(cli_task_line):
        # Example:
        # node-1-server  10.0.3.247  nobody    R    node-1-server__977511be-c694-4f4e-a079-7d0179b37141  dfc1f8f5-387f-494b-89ae-d4600bfb7505-S4
        # FYI: the state value is just the first character of the task state (e.g. STAGING => S)
        cli_task_tokens = cli_task_line.split()
        if len(cli_task_tokens) < 6:
            log.warning('Invalid task line from CLI: {}'.format(cli_task_tokens))
            return None
        return Task(
            cli_task_tokens[0],
            cli_task_tokens[1],
            cli_task_tokens[2],
            cli_task_tokens[3],
            cli_task_tokens[4],
            cli_task_tokens[5])


    def __init__(self, name, host, user, state_char, id, agent):
        self.name = name
        self.host = host
        self.user = user
        self.state_char = state_char
        self.id = id
        self.agent = agent


    def __repr__(self):
        return 'Task[name={} host={} user={} state_char={} id={} agent={}]'.format(
            self.name, self.host, self.user, self.state_char, self.id, self.agent)


def get_status_history(task_name: str) -> list:
    '''Returns a list of task status values (of the form 'TASK_STARTING', 'TASK_KILLED', etc) for a given task.
    The returned values are ordered chronologically from first to last.
    '''
    cluster_tasks = sdk_cmd.cluster_request('GET', '/mesos/tasks').json()
    statuses = []
    for cluster_task in cluster_tasks['tasks']:
        if cluster_task['name'] != task_name:
            continue
        statuses += cluster_task['statuses']
    history = [entry['state'] for entry in sorted(statuses, key=lambda x: x['timestamp'])]
    log.info('Status history for task {}: {}'.format(task_name, ', '.join(history)))
    return history


def get_summary(with_completed=False):
    '''Returns a summary of task information as returned by the DC/OS CLI.
    This may be used instead of invoking 'dcos task [--all]' directly.

    Returns a list of Task objects.
    '''

    # Note: We COULD use --json, but there appears to be some fancy handling done in the CLI for the
    # non-json version, particularly around the running user. Just grab the non-"--json" version of things.
    task_lines = sdk_cmd.run_cli('task --all' if with_completed else 'task', print_output=False).split('\n')
    output = []
    for task_line in task_lines[1:]:  # First line is the header line
        task = Task.parse(task_line)
        if task is not None:
            output.append(task)
    log.info('Task summary (with_completed={}):\n- {}'.format(
        with_completed, '\n- '.join([str(e) for e in output])))
    return output


def get_tasks_avoiding_scheduler(service_name, task_name_pattern):
    '''Returns a list of tasks which are not located on the Scheduler's machine.

    Avoid also killing the system that the scheduler is on. This is just to speed up testing.
    In practice, the scheduler would eventually get relaunched on a different node by Marathon and
    we'd be able to proceed with repairing the service from there. However, it takes 5-20 minutes
    for Mesos to decide that the agent is dead. This is also why we perform a manual 'ls' check to
    verify the host is down, rather than waiting for Mesos to tell us.
    '''
    scheduler_ip = shakedown.get_service_ips('marathon', service_name).pop()
    log.info('Scheduler IP: {}'.format(scheduler_ip))

    server_tasks = [
        task for task in get_summary()
        if task_name_pattern.match(task.name) and
        task.name != sdk_package_registry.PACKAGE_REGISTRY_SERVICE_NAME
    ]

    # Always avoid package registry (if present)
    registry_ips = shakedown.get_service_ips(
        sdk_package_registry.PACKAGE_REGISTRY_SERVICE_NAME
    )

    avoid_tasks = [
        task for task in server_tasks
        if (task.host != scheduler_ip and task.host not in registry_ips)
    ]
    log.info('Found tasks avoiding scheduler at {}: {}'.format(scheduler_ip, avoid_tasks))
    return avoid_tasks


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
        all_updated = len(newly_launched_set) == len(new_set) \
            and len(old_remaining_set) == 0 \
            and len(new_set) >= len(old_set)
        if all_updated:
            log.info('All of the tasks{} have updated\n- Old tasks: {}\n- New tasks: {}'.format(
                prefix_clause,
                old_set,
                new_set))
            return all_updated

        # forgive the language a bit, but len('remained') == len('launched'),
        # and similar for the rest of the label for task ids in the log line,
        # so makes for easier reading
        log.info('Waiting for tasks%s to have updated ids:\n'
                 '- Old tasks (remaining): %s\n'
                 '- New tasks (launched): %s',
                 prefix_clause,
                 old_remaining_set,
                 newly_launched_set)

    fn()


def check_tasks_not_updated(service_name, prefix, old_task_ids):
    sdk_plan.wait_for_completed_deployment(service_name)
    sdk_plan.wait_for_completed_recovery(service_name)
    task_ids = get_task_ids(service_name, prefix)
    task_sets = "\n- Old tasks: {}\n- Current tasks: {}".format(sorted(old_task_ids), sorted(task_ids))
    log.info('Checking tasks starting with "{}" have not been updated:{}'.format(prefix, task_sets))
    assert set(old_task_ids).issubset(set(task_ids)), 'Tasks starting with "{}" were updated:{}'.format(prefix,
                                                                                                        task_sets)
