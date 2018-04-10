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
def test_():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)

    sdk_plan.wait_for_completed_deployment(foldered_name)
    assert !os.path.isdir('./tmp')

    #check that directory is created on update.

    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config['env']['HELLO_ISOLATION'] = 'true'
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)

    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    assert os.path.isdir('./tmp')


