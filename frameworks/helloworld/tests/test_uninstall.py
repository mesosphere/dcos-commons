import pytest
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
from tests import config


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME)
        sdk_install.install(config.PACKAGE_NAME, config.DEFAULT_TASK_COUNT)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME)


@pytest.mark.sanity
def test_uninstall():
    config.check_running()

    # add the needed envvar in marathon and confirm that the uninstall "deployment" succeeds:
    marathon_config = sdk_marathon.get_config(config.PACKAGE_NAME)
    env = marathon_config['env']
    env['SDK_UNINSTALL'] = 'w00t'
    sdk_marathon.update_app(config.PACKAGE_NAME, marathon_config)
    sdk_plan.wait_for_completed_deployment(config.PACKAGE_NAME)
    sdk_tasks.check_running(config.PACKAGE_NAME, 0)
