import time

import dcos
import inspect
import os
import shakedown
import subprocess


PACKAGE_NAME = 'data-store'
WAIT_TIME_IN_SECONDS = 15 * 60

TASK_RUNNING_STATE = 'TASK_RUNNING'
DEFAULT_TASK_COUNT = 5 # 2 metadata nodes, 3 data nodes


strict_mode = os.getenv('SECURITY', 'permissive')


def check_health(expected_tasks = DEFAULT_TASK_COUNT):
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


def install(package_version=None):
    if strict_mode == 'strict':
        shakedown.install_package_and_wait(
            package_name=PACKAGE_NAME,
            package_version=package_version,
            options_file=os.path.dirname(os.path.abspath(inspect.getfile(inspect.currentframe()))) + "/strict.json")
    else:
        shakedown.install_package_and_wait(
            package_name=PACKAGE_NAME,
            package_version=package_version,
            options_file=None)


def uninstall():
    print('Uninstalling/janitoring {}'.format(PACKAGE_NAME))
    try:
        shakedown.uninstall_package_and_wait(PACKAGE_NAME, service_name=PACKAGE_NAME)
    except (dcos.errors.DCOSException, ValueError) as e:
        print('Got exception when uninstalling package, continuing with janitor anyway: {}'.format(e))

    shakedown.run_command_on_master(
        'docker run mesosphere/janitor /janitor.py '
        '-r data-store-role -p data-store-principal -z dcos-service-data-store '
        '--auth_token={}'.format(
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


def get_marathon_config():
    response = dcos.http.get(marathon_api_url('apps/{}/versions'.format(PACKAGE_NAME)))
    assert response.status_code == 200, 'Marathon versions request failed'

    version = response.json()['versions'][0]

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
