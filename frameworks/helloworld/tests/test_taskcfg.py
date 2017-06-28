import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_utils

from tests.config import (
    PACKAGE_NAME,
    check_running
)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        install.uninstall(PACKAGE_NAME)
        options = install.get_package_options({ "service": { "spec_file": "examples/taskcfg.yml" } })
        # don't wait for install to complete successfully:
        shakedown.install_package(PACKAGE_NAME, options_json=options)

        yield # let the test session execute
    finally:
        install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_deploy():
    wait_time = 30
    # taskcfg.yml will initially fail to deploy because several options are missing in the default
    # marathon.json.mustache. verify that tasks are failing for 30s before continuing.
    sdk_utils.out('Checking that tasks are failing to launch for at least {}s'.format(wait_time))

    # we can get brief blips of TASK_RUNNING but they shouldnt last more than 2-3s:
    consecutive_task_running = 0
    def fn():
        nonlocal consecutive_task_running
        svc_tasks = shakedown.get_service_tasks(PACKAGE_NAME)
        states = [t['state'] for t in svc_tasks]
        sdk_utils.out('Task states: {}'.format(states))
        if 'TASK_RUNNING' in states:
            consecutive_task_running += 1
            assert consecutive_task_running <= 3
        else:
            consecutive_task_running = 0
        return False

    try:
        shakedown.wait_for(lambda: fn(), timeout_seconds=wait_time)
    except shakedown.TimeoutExpired:
        sdk_utils.out('Timeout reached as expected')

    # add the needed envvars in marathon and confirm that the deployment succeeds:
    config = marathon.get_config(PACKAGE_NAME)
    env = config['env']
    del env['SLEEP_DURATION']
    env['TASKCFG_ALL_OUTPUT_FILENAME'] = 'output'
    env['TASKCFG_ALL_SLEEP_DURATION'] = '1000'
    marathon.update_app(PACKAGE_NAME, config)

    check_running()
