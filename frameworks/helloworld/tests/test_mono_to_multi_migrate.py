import logging

import pytest
from tests import config

import sdk_install
import sdk_plan
import sdk_tasks
import sdk_upgrade

log = logging.getLogger(__name__)


@pytest.fixture(scope='function', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_old_tasks_not_relaunched():
    # Install hello-world with single yaml
    sdk_install.install(config.PACKAGE_NAME,
                        config.SERVICE_NAME,
                        3,
                        additional_options={"service": {"yaml": "svc"}})
    hello_task_id = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello')
    # Start update plan with options that have list of yaml files to make it launch in multi service mode
    sdk_upgrade.update_service(config.PACKAGE_NAME,
                               config.SERVICE_NAME,
                               additional_options={"service": {"yamls": "svc,foobar_service_name"}})
    # Ensure the old tasks do not relaunch and new tasks are launched
    sdk_tasks.check_task_not_relaunched(config.SERVICE_NAME,
                                        'hello-0-server',
                                        hello_task_id.pop(),
                                        multiservice_name=config.SERVICE_NAME)
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME, multiservice_name='foobar')
    assert len(sdk_tasks.get_task_ids(config.SERVICE_NAME, 'foo')) == 1


@pytest.mark.sanity
def test_old_tasks_get_relaunched_with_new_config():
    # Install hello-world with single yaml
    sdk_install.install(config.PACKAGE_NAME,
                        config.SERVICE_NAME,
                        3,
                        additional_options={"service": {"yaml": "svc"}})
    hello_task_id = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello')
    # Start update plan with options that have list of yaml files to make it
    # launch in multi service mode with updated config
    sdk_upgrade.update_service(config.PACKAGE_NAME,
                               config.SERVICE_NAME,
                               additional_options={
                                   "service": {
                                       "yamls": "svc,foobar_service_name"
                                   },
                                   "hello": {
                                       "cpus": 0.2
                                   }
                               })
    # Ensure the old tasks DO relaunch
    sdk_tasks.check_task_relaunched(config.SERVICE_NAME, 'hello-0-server', hello_task_id.pop())
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME, multiservice_name='foobar')
    assert len(sdk_tasks.get_task_ids(config.SERVICE_NAME, 'foo')) == 1
