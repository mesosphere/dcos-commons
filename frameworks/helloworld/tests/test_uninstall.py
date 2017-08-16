import pytest

import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    check_running
)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME)
        sdk_install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_uninstall():
    check_running()

    # add the needed envvar in marathon and confirm that the uninstall "deployment" succeeds:
    config = sdk_marathon.get_config(PACKAGE_NAME)
    env = config['env']
    env['SDK_UNINSTALL'] = 'w00t'
    sdk_marathon.update_app(PACKAGE_NAME, config)
    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)
    sdk_tasks.check_running(PACKAGE_NAME, 0)
