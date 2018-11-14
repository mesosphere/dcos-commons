import json
import logging
import pytest

from sdk.testing import sdk_cmd
from sdk.testing import sdk_install
from sdk.testing import sdk_plan
from sdk.testing import sdk_tasks
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {"service": {"yaml": "pod-mount-volume"}}

        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 2, additional_options=options)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_kill_node():
    """kill the node task, verify that the node task is relaunched against the same executor as before"""
    verify_shared_executor("hello-0")

    old_tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME, "hello-0")
    assert len(old_tasks) == 2
    old_node_task = [t for t in old_tasks if t.name == "hello-0-node"][0]
    old_agent_task = [t for t in old_tasks if t.name == "hello-0-agent"][0]

    sdk_cmd.kill_task_with_pattern(
        "node-container-path/output",  # hardcoded in cmd, see yml
        "nobody",
        agent_host=old_node_task.host,
    )

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0-node", [old_node_task.id])
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)
    sdk_tasks.check_tasks_not_updated(config.SERVICE_NAME, "hello-0-agent", [old_agent_task.id])

    # the first verify_shared_executor call deleted the files. only the nonessential file came back via its relaunch.
    verify_shared_executor("hello-0")


@pytest.mark.sanity
def test_kill_agent():
    """kill the agent task, verify that the agent task is relaunched against the same executor as before"""
    verify_shared_executor("hello-0")

    old_tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME, "hello-0")
    assert len(old_tasks) == 2
    old_node_task = [t for t in old_tasks if t.name == "hello-0-node"][0]
    old_agent_task = [t for t in old_tasks if t.name == "hello-0-agent"][0]

    sdk_cmd.kill_task_with_pattern(
        "agent-container-path/output",  # hardcoded in cmd, see yml
        "nobody",
        agent_host=old_agent_task.host,
    )

    sdk_tasks.check_tasks_not_updated(config.SERVICE_NAME, "hello-0-node", [old_node_task.id])
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0-agent", [old_agent_task.id])

    # the first verify_shared_executor call deleted the files. only the nonessential file came back via its relaunch.
    verify_shared_executor("hello-0")


def verify_shared_executor(pod_name):
    """verify that both tasks share the same executor:
    - matching ExecutorInfo
    - both 'essential' and 'nonessential' present in shared-volume/ across both tasks
    """
    rc, stdout, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod info {}".format(pod_name)
    )
    assert rc == 0, "Pod info failed"
    tasks = json.loads(stdout)
    assert len(tasks) == 2

    # check that the task executors all match
    executor = tasks[0]["info"]["executor"]
    for task in tasks[1:]:
        assert executor == task["info"]["executor"]
