import json
import logging
import os.path
import pytest

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_tasks
import sdk_utils
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {"service": {"yaml": "nonessential_tasks"}}

        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 2, additional_options=options)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_kill_essential():
    """kill the essential task, verify that both tasks are relaunched against a matching executor"""
    verify_shared_executor("hello-0")

    old_tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME, "hello-0")
    assert len(old_tasks) == 2

    # kill the essential task process. both tasks are on the same pod, so same host:
    sdk_cmd.kill_task_with_pattern(
        "shared-volume/essential",  # hardcoded in cmd, see yml
        "nobody",
        agent_host=old_tasks[0].host,
    )

    # wait for both task ids to change...
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0", [t.id for t in old_tasks])
    # ...and for tasks to be up and running
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)

    # the first verify_shared_executor call deleted the files. both should have come back via the relaunch.
    verify_shared_executor("hello-0", delete_files=False)  # leave files as-is for the next test


@pytest.mark.sanity
def test_kill_nonessential():
    """kill the nonessential task, verify that the nonessential task is relaunched against the same executor as before"""
    verify_shared_executor("hello-0")

    old_tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME, "hello-0")
    assert len(old_tasks) == 2
    old_essential_task = [t for t in old_tasks if t.name == "hello-0-essential"][0]
    old_nonessential_task = [t for t in old_tasks if t.name == "hello-0-nonessential"][0]

    # kill the nonessential task process. both tasks are in the same pod, so same host:
    sdk_cmd.kill_task_with_pattern(
        "shared-volume/nonessential",  # hardcoded in cmd, see yml
        "nobody",
        agent_host=old_nonessential_task.host,
    )

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0-nonessential", [old_nonessential_task.id])
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)
    sdk_tasks.check_tasks_not_updated(config.SERVICE_NAME, "hello-0-essential", [old_essential_task.id])

    # the first verify_shared_executor call deleted the files. only the nonessential file came back via its relaunch.
    verify_shared_executor("hello-0", expected_files=["nonessential"])


def verify_shared_executor(
    pod_name, expected_files=["essential", "nonessential"], delete_files=True
):
    """verify that both tasks share the same executor:
    - matching ExecutorInfo
    - both 'essential' and 'nonessential' present in shared-volume/ across both tasks
    """
    rc, stdout, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "pod info {}".format(pod_name), print_output=False
    )
    assert rc == 0, "Pod info failed"
    try:
        tasks = json.loads(stdout)
    except Exception:
        log.exception("Failed to parse pod info: {}".format(stdout))
        assert False, "Failed to parse pod info, see above"
    assert len(tasks) == 2, "Expected 2 tasks: {}".format(stdout)

    # check that the task executors all match
    executor = tasks[0]["info"]["executor"]
    for task in tasks[1:]:
        assert executor == task["info"]["executor"]

    # for each task, check shared volume content matches what's expected
    task_names = [task["info"]["name"] for task in tasks]
    for task_name in task_names:
        # 1.9 just uses the host filesystem in 'task exec', so use 'task ls' across the board instead
        filenames = sdk_cmd.run_cli("task ls {} shared-volume/".format(task_name))[1].strip().split()
        assert set(expected_files) == set(filenames)

    # delete files from volume in preparation for a following task relaunch
    if delete_files:
        if sdk_utils.dcos_version_less_than("1.10"):
            # 1.9 just uses the host filesystem in 'task exec', so figure out the absolute volume path manually
            expected_file_path = sdk_cmd.service_task_exec(
                config.SERVICE_NAME,
                task_names[0],
                "find /var/lib/mesos/slave/volumes -iname " + filenames[0],
            )[1].strip()
            # volume dir is parent of the expected file path.
            volume_dir = os.path.dirname(expected_file_path)
        else:
            # 1.10+ works correctly: path is relative to sandbox
            volume_dir = "shared-volume/"
        sdk_cmd.service_task_exec(
            config.SERVICE_NAME,
            task_names[0],
            "rm " + " ".join([os.path.join(volume_dir, name) for name in filenames]),
        )
