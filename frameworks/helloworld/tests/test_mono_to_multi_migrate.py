import logging

import pytest
from tests import config

import sdk_install
import sdk_plan
import sdk_tasks
import sdk_upgrade

log = logging.getLogger(__name__)


@pytest.fixture(scope='function', autouse=True)
def before_each_test(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        # Install hello-world with single yaml
        sdk_install.install(config.PACKAGE_NAME,
                            config.SERVICE_NAME,
                            3,
                            additional_options={"service": {"yaml": "svc"}})
        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_old_tasks_not_relaunched():
    hello_task_id = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello')
    assert len(hello_task_id) > 0, 'Got an empty list of task_ids'
    # Start update plan with options that have list of yaml files to make it launch in multi service mode
    sdk_upgrade.update_service(config.PACKAGE_NAME,
                               config.SERVICE_NAME,
                               additional_options={
                                   "service": {
                                       "yaml": "",
                                       "yamls": "svc,foobar_service_name"
                                   }
                               })
    # Ensure new tasks are launched but the old task does not relaunch
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME, multiservice_name='foobar')
    sdk_tasks.check_task_not_relaunched(config.SERVICE_NAME,
                                        'hello-0-server',
                                        hello_task_id.pop(),
                                        multiservice_name=config.SERVICE_NAME)
    assert len(sdk_tasks.get_task_ids(config.SERVICE_NAME, 'foo')) == 1


@pytest.mark.sanity
def test_old_tasks_get_relaunched_with_new_config():
    hello_task_id = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello')
    assert len(hello_task_id) > 0, 'Got an empty list of task_ids'
    # Start update plan with options that have list of yaml files to make it
    # launch in multi service mode with updated config
    sdk_upgrade.update_service(config.PACKAGE_NAME,
                               config.SERVICE_NAME,
                               additional_options={
                                   "service": {
                                       "yaml": "",
                                       "yamls": "svc,foobar_service_name"
                                   },
                                   "hello": {
                                       "cpus": 0.2
                                   }
                               })
    # Ensure the old task DOES relaunch
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME, multiservice_name='foobar')
    sdk_tasks.check_task_relaunched(config.SERVICE_NAME, 'hello-0-server', hello_task_id.pop())
    assert len(sdk_tasks.get_task_ids(config.SERVICE_NAME, 'foo')) == 1
