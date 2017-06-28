import pytest

import sdk_install as install
import sdk_marathon as marathon
import sdk_plan as plan
import sdk_tasks as tasks
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    check_running
)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        install.uninstall(PACKAGE_NAME)
        install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)

        yield # let the test session execute
    finally:
        install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_uninstall():
    check_running()

    # add the needed envvar in marathon and confirm that the uninstall "deployment" succeeds:
    config = marathon.get_config(PACKAGE_NAME)
    env = config['env']
    env['SDK_UNINSTALL'] = 'w00t'
    marathon.update_app(PACKAGE_NAME, config)
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    tasks.check_running(PACKAGE_NAME, 0)
