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
            additional_options={"service": {"name": foldered_name, "yaml": "isolation"}})

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.mark.sanity
def test_tmp_directory_created():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)

    sdk_plan.wait_for_completed_deployment(foldered_name)

    code, stdout, stderr  = sdk_cmd.service_task_exec(config.SERVICE_NAME, "hello-0-server", "echo foo > /tmp/foo")

    assert code > 0

    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config['env']['HELLO_ISOLATION'] = 'true'
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)

    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)


    code, stdout, stderr  = sdk_cmd.service_task_exec(config.SERVICE_NAME, "hello-0-server", "echo bar > /tmp/bar")

    assert code > 0

    code, stdout, stderr  = sdk_cmd.service_task_exec(config.SERVICE_NAME, "hello-0-server", "cat /tmp/bar |  grep bar")

    assert code > 0





