import logging

import pytest
import retrying
import sdk_install
import sdk_marathon
import shakedown
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        # don't wait for install to complete successfully:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            0,
            { "service": { "spec_file": "examples/taskcfg.yml" } },
            wait_for_deployment=False)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_deploy():
    wait_time = 30
    # taskcfg.yml will initially fail to deploy because several options are missing in the default
    # sdk_marathon.json.mustache. verify that tasks are failing for 30s before continuing.
    log.info('Checking that tasks are failing to launch for at least {}s'.format(wait_time))

    # we can get brief blips of TASK_RUNNING but they shouldnt last more than 5-10s:
    consecutive_task_running = 0

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=1000*wait_time,
        retry_on_result=lambda res: not res)
    def wait():
        nonlocal consecutive_task_running
        svc_tasks = shakedown.get_service_tasks(config.SERVICE_NAME)
        states = [t['state'] for t in svc_tasks]
        log.info('Task states: {}'.format(states))
        if 'TASK_RUNNING' in states:
            consecutive_task_running += 1
            assert consecutive_task_running <= 10 # in practice, can see as many as 5 in a row
        else:
            consecutive_task_running = 0
        return False

    try:
        wait()
    except retrying.RetryError:
        log.info('Timeout reached as expected')

    # add the needed envvars in marathon and confirm that the deployment succeeds:
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    env = marathon_config['env']
    del env['SLEEP_DURATION']
    env['TASKCFG_ALL_OUTPUT_FILENAME'] = 'output'
    env['TASKCFG_ALL_SLEEP_DURATION'] = '1000'
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)

    config.check_running()
