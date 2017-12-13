import logging
import os

import pytest

import sdk_cmd
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils
from tests import config

log = logging.getLogger(__name__)

FRAMEWORK_NAME = "secrets/hello-world"
NUM_HELLO = 2
NUM_WORLD = 3

# check environment first...
if "FRAMEWORK_NAME" in os.environ:
    FRAMEWORK_NAME = os.environ["FRAMEWORK_NAME"]
if "NUM_HELLO" in os.environ:
    NUM_HELLO = int(os.environ["NUM_HELLO"])
if "NUM_WORLD" in os.environ:
    NUM_WORLD = int(os.environ["NUM_WORLD"])


@pytest.mark.soak_upgrade
def test_soak_upgrade_downgrade():
    sdk_upgrade.soak_upgrade_downgrade(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT)


@pytest.mark.soak_secrets_update
@pytest.mark.dcos_min_version('1.10')
def test_soak_secrets_update():

    secret_content_alternative = "hello-world-secret-data-alternative"
    test_soak_secrets_framework_alive()

    sdk_cmd.run_cli("package install --cli dcos-enterprise-cli --yes")
    sdk_cmd.run_cli("package install --cli hello-world --yes")
    sdk_cmd.run_cli("security secrets update --value={} secrets/secret1".format(secret_content_alternative))
    sdk_cmd.run_cli("security secrets update --value={} secrets/secret2".format(secret_content_alternative))
    sdk_cmd.run_cli("security secrets update --value={} secrets/secret3".format(secret_content_alternative))
    test_soak_secrets_restart_hello0()

    # get new task ids - only first pod
    hello_tasks = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "hello-0")
    world_tasks = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "world-0")

    # make sure content is changed
    assert secret_content_alternative == sdk_tasks.task_exec(world_tasks[0], "bash -c 'echo $WORLD_SECRET1_ENV'")[1]
    assert secret_content_alternative == sdk_tasks.task_exec(world_tasks[0], "cat WORLD_SECRET2_FILE")[1]
    assert secret_content_alternative == sdk_tasks.task_exec(world_tasks[0], "cat secrets/secret3")[1]

    # make sure content is changed
    assert secret_content_alternative == sdk_tasks.task_exec(hello_tasks[0], "bash -c 'echo $HELLO_SECRET1_ENV'")[1]
    assert secret_content_alternative == sdk_tasks.task_exec(hello_tasks[0], "cat HELLO_SECRET1_FILE")[1]
    assert secret_content_alternative == sdk_tasks.task_exec(hello_tasks[0], "cat HELLO_SECRET2_FILE")[1]

    # revert back to some other value
    sdk_cmd.run_cli("security secrets update --value=SECRET1 secrets/secret1")
    sdk_cmd.run_cli("security secrets update --value=SECRET2 secrets/secret2")
    sdk_cmd.run_cli("security secrets update --value=SECRET3 secrets/secret3")
    test_soak_secrets_restart_hello0()


@pytest.mark.soak_secrets_alive
@pytest.mark.dcos_min_version('1.10')
def test_soak_secrets_framework_alive():

    sdk_plan.wait_for_completed_deployment(FRAMEWORK_NAME)
    sdk_tasks.check_running(FRAMEWORK_NAME, NUM_HELLO + NUM_WORLD)


def test_soak_secrets_restart_hello0():

    hello_tasks_old = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "hello-0")
    world_tasks_old = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "world-0")

    # restart pods to retrieve new secret's content
    sdk_cmd.svc_cli(config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod restart hello-0')
    sdk_cmd.svc_cli(config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod restart world-0')

    # wait pod restart to complete
    sdk_tasks.check_tasks_updated(FRAMEWORK_NAME, "hello-0", hello_tasks_old)
    sdk_tasks.check_tasks_updated(FRAMEWORK_NAME, 'world-0', world_tasks_old)

    # wait till it all running
    sdk_tasks.check_running(FRAMEWORK_NAME, NUM_HELLO + NUM_WORLD)
