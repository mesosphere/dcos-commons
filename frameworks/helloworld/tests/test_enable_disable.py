import json
import pytest

import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
from tests import config


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            2,
            additional_options={ "service": { "spec_file": "examples/enable-disable.yml" } })

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_disable():
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    sdk_plan.recovery_plan_is_empty(config.SERVICE_NAME)
    sdk_tasks.check_running(config.SERVICE_NAME, 2)
    set_test_boolean('false')
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    sdk_tasks.check_running(config.SERVICE_NAME, 1)
    sdk_plan.recovery_plan_is_empty(config.SERVICE_NAME)


@pytest.mark.sanity
def test_enable():
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    sdk_plan.recovery_plan_is_empty(config.SERVICE_NAME)
    sdk_tasks.check_running(config.SERVICE_NAME, 1)
    set_test_boolean('true')
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    sdk_tasks.check_running(config.SERVICE_NAME, 2)
    sdk_plan.recovery_plan_is_empty(config.SERVICE_NAME)


def set_test_boolean(value):
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config['env']['TEST_BOOLEAN'] = value
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)
