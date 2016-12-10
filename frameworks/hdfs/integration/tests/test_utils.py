import os
import time

import dcos
import inspect
import os
import shakedown
import subprocess


PACKAGE_NAME = 'hdfs'
TASK_RUNNING_STATE = 'TASK_RUNNING'
WAIT_TIME_IN_SECONDS = 15 * 60
DEFAULT_HDFS_TASK_COUNT = 10 # 3 data nodes, 3 journal nodes, 2 name nodes, 2 zkfc nodes
HDFS_POD_TYPES = {"journal", "name", "data"}


# expected SECURITY values: 'permissive', 'strict', 'disabled'
if os.environ.get('SECURITY', '') == 'strict':
    print('Using strict mode test configuration')
    PRINCIPAL = 'service-acct'
    DEFAULT_OPTIONS_DICT = {
        "service": {
            "principal": PRINCIPAL,
            "secret_name": "secret"
        }
    }
else:
    print('Using default test configuration')
    PRINCIPAL = 'hdfs-principal'
    DEFAULT_OPTIONS_DICT = {}


def check_health(expected_tasks = DEFAULT_HDFS_TASK_COUNT):
    def fn():
        try:
            return shakedown.get_service_tasks(PACKAGE_NAME)
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(tasks):
        running_tasks = [t for t in tasks if t['state'] == TASK_RUNNING_STATE]
        print('Waiting for {} healthy tasks, got {}/{}'.format(
            expected_tasks, len(running_tasks), len(tasks)))
        return (
            len(running_tasks) >= expected_tasks,
            'Service did not become healthy'
        )

    return spin(fn, success_predicate)

def get_deployment_plan():
    def fn():
        try:
            return dcos.http.get(shakedown.dcos_service_url(PACKAGE_NAME) + "/v1/plans/deploy")
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(response):
        print('Waiting for 200 response')
        success = False

        if hasattr(response, 'status_code'):
            success = response.status_code == 200

        return (
            success,
            'Failed to reach deployment endpoint'
        )

    return spin(fn, success_predicate)


def install(package_version=None, package_name=PACKAGE_NAME, additional_options = {}):
    merged_options = _nested_dict_merge(DEFAULT_OPTIONS_DICT, additional_options)
    print('Installing {} with options: {}'.format(PACKAGE_NAME, merged_options))
    shakedown.install_package_and_wait(
        package_name=PACKAGE_NAME,
        package_version=package_version,
        options_json=merged_options)


def install(additional_options = {}):
    merged_options = _nested_dict_merge(DEFAULT_OPTIONS_DICT, additional_options)
    print('Installing {} with options: {}'.format(PACKAGE_NAME, merged_options))
    shakedown.install_package_and_wait(PACKAGE_NAME, options_json=merged_options)


def uninstall():
    print('Uninstalling/janitoring {}'.format(PACKAGE_NAME))
    try:
        shakedown.uninstall_package_and_wait(PACKAGE_NAME, service_name=PACKAGE_NAME)
    except (dcos.errors.DCOSException, ValueError) as e:
        print('Got exception when uninstalling package, continuing with janitor anyway: {}'.format(e))

    shakedown.run_command_on_master(
        'docker run mesosphere/janitor /janitor.py '
        '-r hdfs-role -p hdfs-principal -z dcos-service-hdfs '
        '--auth_token={}'.format(
            PRINCIPAL,
            shakedown.run_dcos_command(
                'config show core.dcos_acs_token'
            )[0].strip()
        )
    )


def spin(fn, success_predicate, *args, **kwargs):
    end_time = time.time() + WAIT_TIME_IN_SECONDS
    while time.time() < end_time:
        try:
            result = fn(*args, **kwargs)
        except Exception as e:
            is_successful, error_message = False, str(e)
        else:
            is_successful, error_message = success_predicate(result)

        if is_successful:
            print('Success state reached, exiting spin. prev_err={}'.format(error_message))
            break
        print('Waiting for success state... err={}'.format(error_message))
        time.sleep(1)

    assert is_successful, error_message

    return result

def _nested_dict_merge(a, b, path=None):
    "ripped from http://stackoverflow.com/questions/7204805/dictionaries-of-dictionaries-merge"
    if path is None: path = []
    a = a.copy()
    for key in b:
        if key in a:
            if isinstance(a[key], dict) and isinstance(b[key], dict):
                _nested_dict_merge(a[key], b[key], path + [str(key)])
            elif a[key] == b[key]:
                pass # same leaf value
            else:
                raise Exception('Conflict at %s' % '.'.join(path + [str(key)]))
        else:
            a[key] = b[key]
    return a

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


def request(request_fn, *args, **kwargs):
    def success_predicate(response):
        return (
            response.status_code == 200,
            'Request failed: %s' % response.content,
        )

    return spin(request_fn, success_predicate, *args, **kwargs)


def run_dcos_cli_cmd(cmd):
    print('Running {}'.format(cmd))
    stdout = subprocess.check_output(cmd, shell=True).decode('utf-8')
    print(stdout)
    return stdout
