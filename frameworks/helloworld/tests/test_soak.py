import pytest
import shakedown
import time
import json
import os

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_tasks
import sdk_marathon
import sdk_test_upgrade
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

FRAMEWORK_NAME = "secrets/hello-world"
NUM_HELLO = 2
NUM_WORLD = 3

# check environment first...
if "FRAMEWORK_NAME" in os.environ:
    FRAMEWORK_NAME = os.environ["FRAMEWORK_NAME"]
if "NUM_HELLO" in os.environ:
    NUM_HELLO = os.environ["NUM_HELLO"]
if "NUM_WORLD" in os.environ:
    NUM_WORLD = os.environ["NUM_WORLD"]


@pytest.mark.soak_upgrade
def test_soak_upgrade_downgrade():
    sdk_test_upgrade.soak_upgrade_downgrade(PACKAGE_NAME, PACKAGE_NAME, DEFAULT_TASK_COUNT)


@pytest.mark.soak_secrets_update
@pytest.mark.skipif('shakedown.dcos_version_less_than("1.10")')
def test_soak_secrets_update():

    secret_content_alternative = "hello-world-secret-data-alternative"
    test_soak_secrets_framework_alive()

    sdk_cmd.run_cli("security secrets update --value={} secrets/secret1".format(secret_content_alternative))
    sdk_cmd.run_cli("security secrets update --value={} secrets/secret2".format(secret_content_alternative))
    sdk_cmd.run_cli("security secrets update --value={} secrets/secret3".format(secret_content_alternative))
    test_soak_secrets_restart_hello0()

    # get new task ids - only first pod
    hello_tasks = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "hello-0")
    world_tasks = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "world-0")

    # make sure content is changed
    assert secret_content_alternative == task_exec(world_tasks[0], "bash -c 'echo $WORLD_SECRET1_ENV'")
    assert secret_content_alternative == task_exec(world_tasks[0], "cat WORLD_SECRET2_FILE")
    assert secret_content_alternative == task_exec(world_tasks[0], "cat secrets/secret3")

    # make sure content is changed
    assert secret_content_alternative == task_exec(hello_tasks[0], "bash -c 'echo $HELLO_SECRET1_ENV'")
    assert secret_content_alternative == task_exec(hello_tasks[0], "cat HELLO_SECRET1_FILE")
    assert secret_content_alternative == task_exec(hello_tasks[0], "cat HELLO_SECRET2_FILE")

    # revert back to some other value
    sdk_cmd.run_cli("security secrets update --value=SECRET1 secrets/secret1")
    sdk_cmd.run_cli("security secrets update --value=SECRET2 secrets/secret2")
    sdk_cmd.run_cli("security secrets update --value=SECRET3 secrets/secret3")
    test_soak_secrets_restart_hello0()


@pytest.mark.soak_secrets_alive
@pytest.mark.skipif('shakedown.dcos_version_less_than("1.10")')
def test_soak_secrets_framework_alive():

    sdk_plan.wait_for_completed_deployment(FRAMEWORK_NAME)
    sdk_tasks.check_running(FRAMEWORK_NAME, NUM_HELLO + NUM_WORLD)


def test_soak_secrets_restart_hello0():

    hello_tasks_old = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "hello-0")
    world_tasks_old = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "world-0")

    # restart pods to retrieve new secret's content
    sdk_cmd.run_cli('hello-world --name={} pod restart hello-0'.format(FRAMEWORK_NAME))
    sdk_cmd.run_cli('hello-world --name={} pod restart world-0'.format(FRAMEWORK_NAME))

    # wait pod restart to complete
    sdk_tasks.check_tasks_updated(FRAMEWORK_NAME, "hello-0", hello_tasks_old)
    sdk_tasks.check_tasks_updated(FRAMEWORK_NAME, 'world-0', world_tasks_old)

    # wait till it all running
    sdk_tasks.check_running(FRAMEWORK_NAME, NUM_HELLO + NUM_WORLD)


def task_exec(task_name, command):

    lines = sdk_cmd.run_cli("task exec {} {}".format(task_name, command)).split('\n')
    print(lines)
    for i in lines:
        # ignore text starting with:
        #    Overwriting Environment Variable ....
        #    Overwriting PATH ......
        if not i.isspace() and not i.startswith("Overwriting"):
            return i
    return ""

