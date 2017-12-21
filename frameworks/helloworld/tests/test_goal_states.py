import logging
import re

import dcos.marathon
import pytest
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils
import shakedown
from tests import config


log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
        sdk_install.install(
            config.PACKAGE_NAME,
            foldered_name,
            config.DEFAULT_TASK_COUNT,
            additional_options={"service": {"name": foldered_name, "spec_file": "examples/finish_state.yml"}})

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.mark.sanity
def test_install():
    config.check_running(sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.sanity
def test_once_task_does_not_restart_on_config_update():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)

    sdk_plan.wait_for_completed_deployment(foldered_name)
    task_name = 'hello-0-once'
    hello_once_id = sdk_tasks.get_completed_task_id(task_name)
    assert hello_once_id is not None
    log.info('hello_once_id: ' + str(hello_once_id))

    updated_cpus = config.bump_hello_cpus(foldered_name)

    sdk_tasks.check_task_not_relaunched(foldered_name, task_name, hello_once_id)
    config.check_running(foldered_name)


@pytest.mark.sanity
def test_finish_task_restarts_on_config_update():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)
    task_name = 'world-0-finish'
    world_finish_id = sdk_tasks.get_completed_task_id(task_name)
    assert world_finish_id is not None
    log.info('world_finish_id: ' + str(world_finish_id))

    updated_cpus = config.bump_world_cpus(foldered_name)

    sdk_tasks.check_task_relaunched(task_name, world_finish_id)
    config.check_running(foldered_name)
