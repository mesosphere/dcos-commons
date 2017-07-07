import pytest
from shakedown import *
import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan
import sdk_tasks as tasks
import sdk_marathon as marathon
import time
import json
import os
import sdk_test_upgrade
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

FRAMEWORK_NAME = "secrets/hello-world"
NUM_HELLO = 2
NUM_WORLD = 3

if "FRAMEWORK_NAME" in os.environ:
    FRAMEWORK_NAME = os.environ("FRAMEWORK_NAME")
if "NUM_HELLO" in os.environ:
    NUM_HELLO = os.environ("NUM_HELLO")
if "NUM_WORLD" in os.environ:
    NUM_WORLD = os.environ("NUM_WORLD")


@pytest.mark.soak_upgrade
def test_soak_upgrade_downgrade():
    sdk_test_upgrade.soak_upgrade_downgrade(PACKAGE_NAME, PACKAGE_NAME, DEFAULT_TASK_COUNT)


@pytest.mark.soak_secrets_update
@dcos_1_10
def test_soak_secrets_update():

    secret_content_alternative = "hello-world-secret-data-alternative"

    plan.wait_for_completed_deployment(FRAMEWORK_NAME)
    tasks.check_running(FRAMEWORK_NAME, NUM_HELLO + NUM_WORLD)

    cmd.run_cli("security secrets update --value={} secrets/secret1".format(secret_content_alternative))
    cmd.run_cli("security secrets update --value={} secrets/secret2".format(secret_content_alternative))
    cmd.run_cli("security secrets update --value={} secrets/secret3".format(secret_content_alternative))

    hello_tasks_old = tasks.get_task_ids(FRAMEWORK_NAME, "hello-0")
    world_tasks_old = tasks.get_task_ids(FRAMEWORK_NAME, "world-0")

    # restart pods to retrieve new secret's content
    cmd.run_cli('hello-world pods restart hello-0')
    cmd.run_cli('hello-world pods restart world-0')

    # wait pod restart to complete
    tasks.check_tasks_updated(FRAMEWORK_NAME, "hello-0", hello_tasks_old)
    tasks.check_tasks_updated(FRAMEWORK_NAME, 'world-0', world_tasks_old)

    # wait till it is running
    tasks.check_running(FRAMEWORK_NAME, NUM_HELLO + NUM_WORLD)

    # get new task ids - only first pod
    hello_tasks = tasks.get_task_ids(FRAMEWORK_NAME, "hello-0")
    world_tasks = tasks.get_task_ids(FRAMEWORK_NAME, "world-0")

    # make sure content is changed
    assert secret_content_alternative == task_exec(world_tasks[0], "bash -c 'echo $WORLD_SECRET1_ENV'")
    assert secret_content_alternative == task_exec(world_tasks[0], "cat WORLD_SECRET2_FILE")
    assert secret_content_alternative == task_exec(world_tasks[0], "cat {}/secret3".format(PACKAGE_NAME))

    # make sure content is changed
    assert secret_content_alternative == task_exec(hello_tasks[0], "bash -c 'echo $HELLO_SECRET1_ENV'")
    assert secret_content_alternative == task_exec(hello_tasks[0], "cat HELLO_SECRET1_FILE")
    assert secret_content_alternative == task_exec(hello_tasks[0], "cat HELLO_SECRET2_FILE")

    # revert back
    cmd.run_cli("security secrets update --value=SECRET1 secrets/secret1")
    cmd.run_cli("security secrets update --value=SECRET2 secrets/secret2")
    cmd.run_cli("security secrets update --value=SECRET3 secrets/secret3")

    hello_tasks_old = tasks.get_task_ids(FRAMEWORK_NAME, "hello-0")
    world_tasks_old = tasks.get_task_ids(FRAMEWORK_NAME, "world-0")

    # restart pods to retrieve new secret's content
    cmd.run_cli('hello-world pods restart hello-0')
    cmd.run_cli('hello-world pods restart world-0')

    # wait pod restart to complete
    tasks.check_tasks_updated(FRAMEWORK_NAME, "hello-0", hello_tasks_old)
    tasks.check_tasks_updated(FRAMEWORK_NAME, 'world-0', world_tasks_old)


