import json
import pytest

import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
from tests.config import (
    PACKAGE_NAME,
    SERVICE_NAME
)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME, SERVICE_NAME)
        sdk_install.install(
            PACKAGE_NAME,
            SERVICE_NAME,
            2,
            additional_options={ "service": { "spec_file": "examples/enable-disable.yml" } })

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME, SERVICE_NAME)


@pytest.mark.sanity
def test_disable():
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)
    sdk_plan.recovery_plan_is_empty(SERVICE_NAME)
    sdk_tasks.check_running(SERVICE_NAME, 2)
    set_test_boolean('false')
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)
    sdk_tasks.check_running(SERVICE_NAME, 1)
    sdk_plan.recovery_plan_is_empty(SERVICE_NAME)


@pytest.mark.sanity
def test_enable():
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)
    sdk_plan.recovery_plan_is_empty(SERVICE_NAME)
    sdk_tasks.check_running(SERVICE_NAME, 1)
    set_test_boolean('true')
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)
    sdk_tasks.check_running(SERVICE_NAME, 2)
    sdk_plan.recovery_plan_is_empty(SERVICE_NAME)


def set_test_boolean(value):
    config = sdk_marathon.get_config(SERVICE_NAME)
    config['env']['TEST_BOOLEAN'] = value
    sdk_marathon.update_app(SERVICE_NAME, config)
