# NOTE: THIS FILE IS INTENTIONALLY NAMED TO BE RUN LAST. SEE test_shutdown_host().

import logging
import pytest
import re

import sdk_agents
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils
import sdk_hosts
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        install_options_helper()
        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_pod_restart():
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, "hello-0")

    # get current agent id:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod info hello-0", json=True, print_output=False
    )
    old_agent = jsonobj[0]["info"]["slaveId"]["value"]

    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod restart hello-0", json=True
    )
    assert len(jsonobj) == 2
    assert jsonobj["pod"] == "hello-0"
    assert len(jsonobj["tasks"]) == 1
    assert jsonobj["tasks"][0] == "hello-0-server"

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0", hello_ids)
    check_healthy()

    # check agent didn't move:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod info hello-0", json=True, print_output=False
    )
    new_agent = jsonobj[0]["info"]["slaveId"]["value"]
    assert old_agent == new_agent


@pytest.mark.sanity
def test_pod_replace():
    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, "world-0")

    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod replace world-0", json=True
    )
    assert len(jsonobj) == 2
    assert jsonobj["pod"] == "world-0"
    assert len(jsonobj["tasks"]) == 1
    assert jsonobj["tasks"][0] == "world-0-server"

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "world-0", world_ids)
    check_healthy()


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.9")
def test_pod_pause_resume():
    """Tests pausing and resuming a pod. Similar to pod restart, except the task is marked with a PAUSED state"""

    # get current agent id:
    taskinfo = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod info hello-0", json=True, print_output=False
    )[0]["info"]
    old_agent = taskinfo["slaveId"]["value"]
    old_cmd = taskinfo["command"]["value"]

    # sanity check of pod status/plan status before we pause/resume:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod status hello-0 --json", json=True
    )
    assert len(jsonobj["tasks"]) == 1
    assert jsonobj["tasks"][0]["name"] == "hello-0-server"
    assert jsonobj["tasks"][0]["status"] == "RUNNING"
    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "plan status deploy --json", json=True
    )["phases"][0]
    assert phase["name"] == "hello"
    assert phase["status"] == "COMPLETE"
    assert phase["steps"][0]["name"] == "hello-0:[server]"
    assert phase["steps"][0]["status"] == "COMPLETE"

    # pause the pod, wait for it to relaunch
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, "hello-0")
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "debug pod pause hello-0", json=True
    )
    assert len(jsonobj) == 2
    assert jsonobj["pod"] == "hello-0"
    assert len(jsonobj["tasks"]) == 1
    assert jsonobj["tasks"][0] == "hello-0-server"

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0", hello_ids)
    # recovery will not be completed due to 'exit 1' readiness check on paused pod.
    # it will be IN_PROGRESS if there are other completed recovery operations (prior test cases), or STARTED if there aren't.
    check_healthy(expected_recovery_state=["STARTED", "IN_PROGRESS"])

    # check agent didn't move, and that the command has changed:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod info hello-0", json=True, print_output=False
    )[0]["info"]
    assert old_agent == jsonobj["slaveId"]["value"]
    cmd = jsonobj["command"]["value"]
    assert "This task is PAUSED" in cmd

    if sdk_utils.dcos_version_at_least("1.10"):
        # validate readiness check (default executor)
        readiness_check = jsonobj["check"]["command"]["command"]["value"]
        assert "exit 1" == readiness_check

    # check PAUSED state in plan and in pod status:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod status hello-0 --json", json=True
    )
    assert len(jsonobj["tasks"]) == 1
    assert jsonobj["tasks"][0]["name"] == "hello-0-server"
    assert jsonobj["tasks"][0]["status"] == "PAUSED"
    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "plan status deploy --json", json=True
    )["phases"][0]
    assert phase["name"] == "hello"
    assert phase["status"] == "COMPLETE"
    assert phase["steps"][0]["name"] == "hello-0:[server]"
    assert phase["steps"][0]["status"] == "PAUSED"

    # resume the pod again, wait for it to relaunch
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, "hello-0")
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "debug pod resume hello-0", json=True
    )
    assert len(jsonobj) == 2
    assert jsonobj["pod"] == "hello-0"
    assert len(jsonobj["tasks"]) == 1
    assert jsonobj["tasks"][0] == "hello-0-server"

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0", hello_ids)
    check_healthy()

    # check again that the agent didn't move:
    taskinfo = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod info hello-0", json=True, print_output=False
    )[0]["info"]
    assert old_agent == taskinfo["slaveId"]["value"]
    assert old_cmd == taskinfo["command"]["value"]

    # check that the pod/plan status is back to normal:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod status hello-0 --json", json=True
    )
    assert len(jsonobj["tasks"]) == 1
    assert jsonobj["tasks"][0]["name"] == "hello-0-server"
    assert jsonobj["tasks"][0]["status"] == "RUNNING"
    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "plan status deploy --json", json=True
    )["phases"][0]
    assert phase["name"] == "hello"
    assert phase["status"] == "COMPLETE"
    assert phase["steps"][0]["name"] == "hello-0:[server]"
    assert phase["steps"][0]["status"] == "COMPLETE"


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.9")
def test_pods_restart_graceful_shutdown():
    install_options_helper(30)

    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, "world-0")

    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod restart world-0", json=True
    )
    assert len(jsonobj) == 2
    assert jsonobj["pod"] == "world-0"
    assert len(jsonobj["tasks"]) == 1
    assert jsonobj["tasks"][0] == "world-0-server"

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "world-0", world_ids)
    check_healthy()

    # ensure the SIGTERM was sent via the "all clean" message in the world
    # service's signal trap/handler, BUT not the shell command, indicated
    # by "echo".
    stdout = sdk_cmd.run_cli("task log --completed --lines=1000 {}".format(world_ids[0]))
    clean_msg = None
    for s in stdout.split("\n"):
        if s.find("echo") < 0 and s.find("all clean") >= 0:
            clean_msg = s

    assert clean_msg is not None


@pytest.mark.sanity
def test_kill_scheduler():
    scheduler_task_prefix = sdk_marathon.get_scheduler_task_prefix(config.SERVICE_NAME)
    scheduler_ids = sdk_tasks.get_task_ids("marathon", scheduler_task_prefix)
    assert len(scheduler_ids) == 1, "Expected to find one scheduler task"

    sdk_cmd.kill_task_with_pattern(
        "./hello-world-scheduler/bin/helloworld",
        sdk_marathon.get_scheduler_host(config.SERVICE_NAME),
    )

    sdk_tasks.check_tasks_updated("marathon", scheduler_task_prefix, scheduler_ids)
    check_healthy()


@pytest.mark.sanity
def test_kill_hello_task():
    hello_task = sdk_tasks.get_service_tasks(config.SERVICE_NAME, task_prefix="hello-0")[0]

    sdk_cmd.kill_task_with_pattern("hello-container-path/output", hello_task.host)

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0", [hello_task.id])
    check_healthy()


@pytest.mark.sanity
def test_kill_world_executor():
    world_task = sdk_tasks.get_service_tasks(config.SERVICE_NAME, task_prefix="world-0")[0]

    sdk_cmd.kill_task_with_pattern("mesos-default-executor", world_task.host)

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "world-0", [world_task.id])
    check_healthy()


@pytest.mark.sanity
def test_kill_all_executors():
    tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME)

    for task in tasks:
        sdk_cmd.kill_task_with_pattern("mesos-default-executor", task.host)

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "", [task.id for task in tasks])
    check_healthy()


@pytest.mark.sanity
def test_kill_master():
    sdk_cmd.kill_task_with_pattern("mesos-master")

    check_healthy()


@pytest.mark.sanity
def test_kill_zk():
    sdk_cmd.kill_task_with_pattern("zookeeper")

    check_healthy()


@pytest.mark.sanity
@pytest.mark.skipif(
    sdk_utils.dcos_version_less_than("1.10"),
    reason="BLOCKED-INFINITY-3203: Skipping recovery tests on 1.9",
)
def test_config_update_while_partitioned():
    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, "world")
    host = sdk_hosts.system_host(config.SERVICE_NAME, "world-0-server")
    sdk_agents.partition_agent(host)

    service_config = sdk_marathon.get_config(config.SERVICE_NAME)
    updated_cpus = float(service_config["env"]["WORLD_CPUS"]) + 0.1
    service_config["env"]["WORLD_CPUS"] = str(updated_cpus)
    sdk_marathon.update_app(service_config, wait_for_completed_deployment=False)

    sdk_agents.reconnect_agent(host)
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "world", world_ids)
    check_healthy()
    all_tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME)
    running_tasks = [
        t for t in all_tasks if t.name.startswith("world") and t.state == "TASK_RUNNING"
    ]
    assert len(running_tasks) == config.world_task_count(config.SERVICE_NAME)
    for t in running_tasks:
        assert config.close_enough(t.resources["cpus"], updated_cpus)


# @@@@@@@
# WARNING: THIS MUST BE THE LAST TEST IN THIS FILE. ANY TEST THAT FOLLOWS WILL BE FLAKY.
# @@@@@@@
@pytest.mark.sanity
def test_shutdown_host():
    candidate_tasks = sdk_tasks.get_tasks_avoiding_scheduler(
        config.SERVICE_NAME, re.compile("^(hello|world)-[0-9]+-server$")
    )
    assert len(candidate_tasks) != 0, "Could not find a node to shut down"

    # Pick the host of the first task from the above list, then get ALL tasks which may be located
    # on that host. We'll need to 'pod replace' all of them.
    replace_hostname = candidate_tasks[0].host
    replace_tasks = [task for task in candidate_tasks if task.host == replace_hostname]
    log.info(
        "Tasks on host {} to be replaced after shutdown: {}".format(replace_hostname, replace_tasks)
    )

    # Instead of partitioning or reconnecting, we shut down the host permanently
    sdk_agents.shutdown_agent(replace_hostname)
    # Reserved resources on this agent are expected to appear as orphaned in Mesos state.
    # Tell our uninstall validation to ignore orphaned resources coming from this agent.
    sdk_install.ignore_dead_agent(replace_hostname)

    # Get pod name from task name: "hello-0-server" => "hello-0"
    replace_pods = set([task.name[: -len("-server")] for task in replace_tasks])
    assert len(replace_pods) == len(
        replace_tasks
    ), "Expected one task per pod in tasks to replace: {}".format(replace_tasks)
    for pod_name in replace_pods:
        sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "pod replace {}".format(pod_name))
    sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)

    # Print another dump of current cluster tasks, now that repair has started.
    sdk_tasks.get_summary()

    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)
    sdk_tasks.check_running(config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)

    # For each task affected by the shutdown, find the new version of it, and check that it moved.
    # Note that the old version on the dead agent may still be present/'running' as
    # Mesos might not have fully acknowledged the agent's death.
    new_tasks = sdk_tasks.get_summary()
    for replaced_task in replace_tasks:
        new_task = [
            task
            for task in new_tasks
            if task.name == replaced_task.name and task.id != replaced_task.id
        ][0]
        log.info(
            "Checking affected task has moved to a new agent:\n"
            "old={}\nnew={}".format(replaced_task, new_task)
        )
        assert replaced_task.agent_id != new_task.agent_id


def install_options_helper(kill_grace_period=0):
    options = {"world": {"kill_grace_period": kill_grace_period, "count": 3}}

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT + 1,
        additional_options=options,
    )


def check_healthy(expected_recovery_state="COMPLETE"):
    config.check_running()
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    sdk_plan.wait_for_plan_status(config.SERVICE_NAME, "recovery", expected_recovery_state)
