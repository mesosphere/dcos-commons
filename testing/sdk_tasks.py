"""Utilities relating to lookup and manipulation of mesos tasks in a service or across the cluster.

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_tasks IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import logging
import retrying

import sdk_agents
import sdk_cmd
import sdk_package_registry
import sdk_plan


DEFAULT_TIMEOUT_SECONDS = 30 * 60

# From dcos-cli:
COMPLETED_TASK_STATES = set(
    [
        "TASK_FINISHED",
        "TASK_KILLED",
        "TASK_FAILED",
        "TASK_LOST",
        "TASK_ERROR",
        "TASK_GONE",
        "TASK_GONE_BY_OPERATOR",
        "TASK_DROPPED",
        "TASK_UNREACHABLE",
        "TASK_UNKNOWN",
    ]
)

log = logging.getLogger(__name__)


def check_running(
    service_name, expected_task_count, timeout_seconds=DEFAULT_TIMEOUT_SECONDS, allow_more=True
):
    agentid_to_hostname = _get_agentid_to_hostname()

    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def _check_running():
        tasks = _get_service_tasks(service_name, agentid_to_hostname)
        running_task_names = []
        other_tasks = []
        for t in tasks:
            if t.state == "TASK_RUNNING":
                running_task_names.append(t.name)
            else:
                other_tasks.append("{}={}".format(t.name, t.state))
        log.info(
            "Waiting for {} {} tasks in {}, got {} running/{} total:\n- running: [{}]\n- other: [{}]".format(
                "at least" if allow_more else "exactly",
                expected_task_count,
                service_name,
                len(running_task_names),
                len(tasks),
                ", ".join(sorted(running_task_names)),
                ", ".join(sorted(other_tasks)),
            )
        )
        if allow_more:
            return len(running_task_names) >= expected_task_count
        else:
            return len(running_task_names) == expected_task_count

    _check_running()


class Task(object):
    """Entry value returned by get_summary() and get_service_tasks()"""

    @staticmethod
    def parse(task_entry, agentid_to_hostname):
        agent_id = task_entry["slave_id"]
        matching_hostname = agentid_to_hostname.get(agent_id)
        if matching_hostname:
            host = matching_hostname
        else:
            host = "UNKNOWN:" + agent_id
        return Task(
            task_entry["name"],
            host,
            task_entry["state"],
            task_entry["id"],
            task_entry["executor_id"],
            task_entry["framework_id"],
            agent_id,
            task_entry["resources"],
        )

    def __init__(self, name, host, state, task_id, executor_id, framework_id, agent_id, resources):
        self.name = name
        self.host = host
        self.state = state  # 'TASK_RUNNING', 'TASK_KILLED', ...
        self.is_completed = state in COMPLETED_TASK_STATES
        self.id = task_id
        self.executor_id = executor_id
        self.framework_id = framework_id
        self.agent_id = agent_id
        self.resources = resources  # 'cpus', 'disk', 'mem', 'gpus' => int

    def __repr__(self):
        return 'Task[name="{}"\tstate={}\tid={}\thost={}\tframework_id={}\tagent_id={}]'.format(
            self.name, self.state, self.id, self.host, self.framework_id, self.agent_id
        )


def get_all_status_history(task_name: str, with_completed_tasks=True) -> list:
    """Returns a list of task status values(of the form 'TASK_STARTING', 'TASK_KILLED', etc) for
    all instances of a given task. The returned values are ordered chronologically from first to
    last.

    If with_completed_tasks is set to False, then the statuses will only be for tasks which are
    currently running. Any statuses from any completed / failed tasks(e.g. from prior tests) will be
    omitted from the returned history.
    """
    cluster_tasks = sdk_cmd.cluster_request("GET", "/mesos/tasks").json()["tasks"]
    statuses = []
    for cluster_task in cluster_tasks:
        if cluster_task["name"] != task_name:
            # Skip task: wrong name
            continue
        if not with_completed_tasks and cluster_task["state"] in COMPLETED_TASK_STATES:
            # Skip task: task instance is completed and we don't want completed tasks
            continue
        statuses += cluster_task["statuses"]
    history = [s for s in sorted(statuses, key=lambda x: x["timestamp"])]
    log.info(
        "Status history for task {} (with_completed={}): {}".format(
            task_name, with_completed_tasks, ", ".join([s["state"] for s in history])
        )
    )
    return history


def get_task_ids(service_name, task_prefix=""):
    return [t.id for t in get_service_tasks(service_name, task_prefix=task_prefix)]


def get_service_tasks(service_name, task_prefix="", with_completed_tasks=False):
    return _get_service_tasks(
        service_name, _get_agentid_to_hostname(), task_prefix, with_completed_tasks
    )


def _get_service_tasks(
    service_name, agentid_to_hostname, task_prefix="", with_completed_tasks=False
):
    """Returns a summary of all tasks in the specified Mesos framework.

    Returns a list of Task objects.
    """
    cluster_frameworks = sdk_cmd.cluster_request("GET", "/mesos/frameworks").json()["frameworks"]
    service_tasks = []
    for fwk in cluster_frameworks:
        if not fwk["name"] == service_name or not fwk["active"]:
            continue
        service_tasks += [Task.parse(entry, agentid_to_hostname) for entry in fwk["tasks"]]
        if with_completed_tasks:
            service_tasks += [
                Task.parse(entry, agentid_to_hostname) for entry in fwk["completed_tasks"]
            ]
    if task_prefix:
        service_tasks = [t for t in service_tasks if t.name.startswith(task_prefix)]
    return service_tasks


def get_summary(with_completed=False, task_name=None):
    """Returns a summary of all cluster tasks in the cluster, or just a specified task.
    This may be used instead of invoking 'dcos task [--all]' directly.

    Returns a list of Task objects.
    """
    cluster_tasks = sdk_cmd.cluster_request("GET", "/mesos/tasks").json()["tasks"]
    agentid_to_hostname = _get_agentid_to_hostname()
    all_tasks = [Task.parse(entry, agentid_to_hostname) for entry in cluster_tasks]
    if with_completed:
        output = all_tasks
    else:
        output = list(filter(lambda t: not t.is_completed, all_tasks))
    if task_name:
        output = list(filter(lambda t: t.name == task_name, all_tasks))
    log.info(
        "Task summary (with_completed={}) (task_name=[{}]):\n- {}".format(
            with_completed, task_name, "\n- ".join([str(e) for e in output])
        )
    )
    return output


def _get_agentid_to_hostname():
    return {agent["id"]: agent["hostname"] for agent in sdk_agents.get_agents()}


def get_tasks_avoiding_scheduler(service_name, task_name_pattern):
    """Returns a list of tasks which are not located on the Scheduler's machine.

    Avoid also killing the system that the scheduler is on. This is just to speed up testing.
    In practice, the scheduler would eventually get relaunched on a different node by Marathon and
    we'd be able to proceed with repairing the service from there. However, it takes 5-20 minutes
    for Mesos to decide that the agent is dead. This is also why we perform a manual 'ls' check to
    verify the host is down, rather than waiting for Mesos to tell us.
    """
    skip_tasks = {sdk_package_registry.PACKAGE_REGISTRY_SERVICE_NAME}
    server_tasks = [
        task
        for task in get_summary()
        if task.name not in skip_tasks and task_name_pattern.match(task.name)
    ]

    agentid_to_hostname = _get_agentid_to_hostname()

    scheduler_ips = [
        t.host
        for t in _get_service_tasks("marathon", agentid_to_hostname, task_prefix=service_name)
    ]
    log.info("Scheduler [{}] IPs: {}".format(service_name, scheduler_ips))

    # Always avoid package registry (if present)
    registry_ips = [
        t.host
        for t in _get_service_tasks(
            "marathon",
            agentid_to_hostname,
            task_prefix=sdk_package_registry.PACKAGE_REGISTRY_SERVICE_NAME,
        )
    ]
    log.info(
        "Package Registry [{}] IPs: {}".format(
            sdk_package_registry.PACKAGE_REGISTRY_SERVICE_NAME, registry_ips
        )
    )

    skip_ips = set(scheduler_ips) | set(registry_ips)
    avoid_tasks = [task for task in server_tasks if task.host not in skip_ips]
    log.info(
        "Found tasks avoiding {} scheduler and {} at {}: {}".format(
            service_name, sdk_package_registry.PACKAGE_REGISTRY_SERVICE_NAME, skip_ips, avoid_tasks
        )
    )
    return avoid_tasks


def check_task_relaunched(
    task_name,
    old_task_id,
    ensure_new_task_not_completed=True,
    timeout_seconds=DEFAULT_TIMEOUT_SECONDS,
):
    log.info(
        'Checking task "{}" is relaunched: old_task_id={}, ensure_new_task_not_completed={}'.format(
            task_name, old_task_id, ensure_new_task_not_completed
        )
    )

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds * 1000,
        retry_on_exception=lambda e: isinstance(e, Exception),
    )
    def _check_task_relaunched():
        tasks = get_summary(with_completed=True, task_name=task_name)
        assert len(tasks) > 0, "No tasks were found with the given task name {}".format(task_name)
        assert (
            len(list(filter(lambda t: t.is_completed and t.id == old_task_id, tasks))) > 0
        ), "Unable to find any completed tasks with id {}".format(old_task_id)
        assert (
            len(
                list(
                    filter(
                        lambda t: t.id != old_task_id
                        and (not t.is_completed if ensure_new_task_not_completed else True),
                        tasks,
                    )
                )
            )
            > 0
        ), "Unable to find any new tasks with name {} with (ensure_new_task_not_completed:{})".format(
            task_name, ensure_new_task_not_completed
        )

    _check_task_relaunched()


def check_scheduler_relaunched(
    service_name: str, old_scheduler_task_id: str, timeout_seconds=DEFAULT_TIMEOUT_SECONDS
):
    """
    This function checks for the relaunch of a task using the same matching as is
    used in sdk_task.get_task_id()
    """

    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def fn():
        task_ids = set([t.id for t in get_service_tasks("marathon", task_prefix=service_name)])
        log.info("Found {} scheduler task ids {}".format(service_name, task_ids))
        return len(task_ids) > 0 and (old_scheduler_task_id not in task_ids or len(task_ids) > 1)

    fn()


def check_task_not_relaunched(
    service_name, task_name, old_task_id, multiservice_name=None, with_completed=False
):
    log.info(
        'Checking that task "{}" with current task id {} is not relaunched'.format(
            task_name, old_task_id
        )
    )
    sdk_plan.wait_for_completed_deployment(service_name, multiservice_name=multiservice_name)
    sdk_plan.wait_for_completed_recovery(service_name, multiservice_name=multiservice_name)

    task_ids = set([t.id for t in get_summary(with_completed) if t.name == task_name])
    assert old_task_id in task_ids, "Old task id {} was not found in task_ids {}".format(
        old_task_id, task_ids
    )
    assert len(task_ids) == 1, "Length != 1. Expected task id {} Task ids: {}".format(
        old_task_id, task_ids
    )


def check_tasks_updated(
    service_name, prefix, old_task_ids, timeout_seconds=DEFAULT_TIMEOUT_SECONDS
):
    prefix_clause = ""
    if prefix:
        prefix_clause = ' starting with "{}"'.format(prefix)

    @retrying.retry(
        wait_fixed=1000, stop_max_delay=timeout_seconds * 1000, retry_on_result=lambda res: not res
    )
    def _check_tasks_updated():
        task_ids = get_task_ids(service_name, prefix)

        old_set = set(old_task_ids)
        new_set = set(task_ids)
        newly_launched_set = new_set.difference(old_set)
        old_remaining_set = old_set.intersection(new_set)
        # the constraints of old and new task cardinality match should be covered by completion of
        # deploy/recovery/whatever plan, not task cardinality, but some uses of this method are not
        # using the plan, so not the definitive source, so will fail when the finished state of a
        # plan yields more or less tasks per pod.
        all_updated = (
            len(newly_launched_set) == len(new_set)
            and len(old_remaining_set) == 0
            and len(new_set) >= len(old_set)
        )
        if all_updated:
            log.info(
                "All of the tasks{} have updated\n- Old tasks: {}\n- New tasks: {}".format(
                    prefix_clause, old_set, new_set
                )
            )
            return all_updated

        # forgive the language a bit, but len('remained') == len('launched'),
        # and similar for the rest of the label for task ids in the log line,
        # so makes for easier reading
        log.info(
            "Waiting for tasks%s to have updated ids:\n"
            "- Old tasks (remaining): %s\n"
            "- New tasks (launched): %s",
            prefix_clause,
            old_remaining_set,
            newly_launched_set,
        )

    log.info(
        "Waiting for tasks%s to have updated ids:\n" "- Old tasks: %s", prefix_clause, old_task_ids
    )
    _check_tasks_updated()


def check_tasks_not_updated(service_name, prefix, old_task_ids):
    sdk_plan.wait_for_completed_deployment(service_name)
    sdk_plan.wait_for_completed_recovery(service_name)
    task_ids = get_task_ids(service_name, prefix)
    task_sets = "\n- Old tasks: {}\n- Current tasks: {}".format(
        sorted(old_task_ids), sorted(task_ids)
    )
    log.info('Checking tasks starting with "{}" have not been updated:{}'.format(prefix, task_sets))
    assert set(old_task_ids).issubset(
        set(task_ids)
    ), 'Tasks starting with "{}" were updated:{}'.format(prefix, task_sets)
