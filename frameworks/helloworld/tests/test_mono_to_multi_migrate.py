import logging

import pytest
from tests import config

import sdk_install
import sdk_tasks
import sdk_upgrade

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

@pytest.mark.tarun
def test_mono_to_multi_migration_simple():
    # Install hello-world with single yaml
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 1, additional_options={
        "service": {
            "yaml": "svc"
        },
        "world": {
            "count": 0
        }
    })
    hello_task_id = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello')
    log.info('Fetched task id(s) : {}'.format(hello_task_id))
    # Start update plan with options that have list of yaml files to make it launch in multi service mode
    sdk_upgrade.update_service(config.PACKAGE_NAME, config.SERVICE_NAME, additional_options={
        "service": {
            "yamls": "svc,foobar_service_name"
        },
        "world": {
            "count": 0
        }
    })
    # Ensure the old tasks do not relaunch
    sdk_tasks.check_task_not_relaunched(config.SERVICE_NAME, 'hello-0-server', hello_task_id.pop(), config.SERVICE_NAME)
    # Ensure new tasks are launched.
    assert len(sdk_tasks.get_task_ids('foobar', 'foo')) > 1
