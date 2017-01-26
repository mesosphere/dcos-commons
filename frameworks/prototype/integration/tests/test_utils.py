import os

import dcos
import shakedown

PACKAGE_NAME = 'prototype'
WAIT_TIME_IN_SECONDS = 15 * 60

TASK_RUNNING_STATE = 'TASK_RUNNING'
SPEC_URL = 'https://gist.githubusercontent.com/mohitsoni/29d03e7d73135d4a8d2ea54b508bbcf9/raw/fb495434dcc507d3afc79fa761afe57bb31975c4/service.yml'

# expected SECURITY values: 'permissive', 'strict', 'disabled'
if os.environ.get('SECURITY', '') == 'strict':
    print('Using strict mode test configuration')
    PRINCIPAL = 'service-acct'

    DEFAULT_OPTIONS_DICT = {
        "service": {
            "principal": PRINCIPAL,
            "secret_name": "secret",
            "name": "prototype",
            "specification_uri": SPEC_URL
        }
    }
else:
    print('Using default test configuration')
    PRINCIPAL = 'prototype-principal'
    DEFAULT_OPTIONS_DICT = {
        "service": {
            "name": "prototype",
            "specification_uri": SPEC_URL
        }
    }


def get_marathon_config():
    response = dcos.http.get(marathon_api_url('apps/{}/versions'.format(PACKAGE_NAME)))
    assert response.status_code == 200, 'Marathon versions request failed'

    last_index = len(response.json()['versions']) - 1
    version = response.json()['versions'][last_index]

    response = dcos.http.get(marathon_api_url('apps/{}/versions/{}'.format(PACKAGE_NAME, version)))
    assert response.status_code == 200

    config = response.json()
    del config['uris']
    del config['version']

    return config


def marathon_api_url(basename):
    return '{}/v2/{}'.format(shakedown.dcos_service_url('marathon'), basename)


def marathon_api_url_with_param(basename, path_param):
    return '{}/{}'.format(marathon_api_url(basename), path_param)


def check_dcos_service_health():
    return shakedown.service_healthy(PACKAGE_NAME)


def tasks_running_success_predicate(task_count):
    tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    running_tasks = [t for t in tasks if t['state'] == TASK_RUNNING_STATE]
    print('Waiting for {} healthy tasks, got {}/{}'.format(task_count, len(running_tasks), len(tasks)))
    return len(running_tasks) == task_count


def wait_for_dcos_tasks_health(task_count):
    return shakedown.wait_for(lambda: tasks_running_success_predicate(task_count), timeout_seconds=WAIT_TIME_IN_SECONDS)


def check_health():
    expected_tasks = 1
    wait_for_dcos_tasks_health(expected_tasks)


def install():
    print('Installing {} with options: {}'.format(PACKAGE_NAME, DEFAULT_OPTIONS_DICT))
    shakedown.install_package(
        package_name=PACKAGE_NAME,
        service_name=PACKAGE_NAME,
        package_version=None,
        options_json=DEFAULT_OPTIONS_DICT,
        wait_for_completion=True)


def uninstall():
    print('Uninstalling/janitoring {}'.format(PACKAGE_NAME))
    try:
        shakedown.uninstall_package_and_wait(PACKAGE_NAME, service_name=PACKAGE_NAME)
    except (dcos.errors.DCOSException, ValueError) as e:
        print('Got exception when uninstalling package, continuing with janitor anyway: {}'.format(e))

    shakedown.run_command_on_master(
        'docker run mesosphere/janitor /janitor.py '
        '-r prototype-role -p prototype-principal -z dcos-service-prototype '
        '--auth_token={}'.format(
            shakedown.run_dcos_command(
                'config show core.dcos_acs_token'
            )[0].strip()
        )
    )
