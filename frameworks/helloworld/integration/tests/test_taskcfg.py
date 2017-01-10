import dcos.http
import json
import pytest
import re
import shakedown
import time

from tests.test_utils import (
    PACKAGE_NAME,
    TASK_RUNNING_STATE,
    WAIT_TIME_IN_SECONDS,
    check_health,
    get_marathon_config,
    get_deployment_plan,
    get_sidecar_plan,
    get_task_count,
    install,
    marathon_api_url,
    request,
    run_dcos_cli_cmd,
    uninstall,
    spin,
    start_sidecar_plan
)


def setup_module(module):
    uninstall()
    options = {
        "service": {
            "spec_file": "examples/taskcfg.yml"
        }
    }

    install(None, PACKAGE_NAME, options)


@pytest.mark.sanity
def test_deploy():
    # taskcfg.yml will initially fail to deploy because several options are missing in the default
    # marathon.json.mustache. verify that tasks are failing for 30s before continuing.
    print('Checking that tasks are failing to launch for at least 30s')
    end_time = time.time() + 30
    # we can get brief blips of TASK_RUNNING but they shouldnt last more than 2-3s:
    consecutive_task_running = 0
    while time.time() < end_time:
        try:
            tasks = shakedown.get_service_tasks(PACKAGE_NAME)
        except Exception as e:
            continue
        states = [t['state'] for t in tasks]
        print('Task states: {}'.format(states))
        if TASK_RUNNING_STATE in states:
            consecutive_task_running += 1
            assert consecutive_task_running <= 3
        else:
            consecutive_task_running = 0
        time.sleep(1)

    # add the needed envvars in marathon and confirm that the deployment succeeds:
    config = get_marathon_config()
    env = config['env']
    del env['SLEEP_DURATION']
    env['TASKCFG_ALL_OUTPUT_FILENAME'] = 'output'
    env['TASKCFG_ALL_SLEEP_DURATION'] ='1000'
    request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    check_health()
