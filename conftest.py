""" This file configures python logging for the pytest framework
integration tests

Note: pytest must be invoked with this file in the working directory
E.G. py.test frameworks/<your-frameworks>/tests
"""
import logging
import os
import subprocess

import pytest
import requests
import sdk_security
import sdk_utils

log_level = os.getenv('TEST_LOG_LEVEL', 'INFO').upper()

log_levels = ('DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL', 'EXCEPTION')

assert log_level in log_levels, '{} is not a valid log level. ' \
    'Use one of: {}'.format(log_level, ', '.join(log_levels))

logging.basicConfig(
    format='[%(asctime)s|%(name)s|%(levelname)s]: %(message)s',
    level=log_level)

log = logging.getLogger(__name__)


def get_task_ids(user: str=None):
    """ This function uses dcos task WITHOUT the JSON options because
    that can return the wrong user for schedulers
    """
    tasks = subprocess.check_output(['dcos', 'task']).decode().split('\n')
    for task_str in tasks[1:]: # First line is the header line
        task = task_str.split()
        if len(task) < 5:
            continue
        if not user or task[2] == user:
            yield task[4]


def get_task_logs_for_id(task_id: str,  task_file: str='stdout', lines: int=1000000):
    result = subprocess.run(
        ['dcos', 'task', 'log', task_id, '--lines', str(lines), task_file],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode:
        errmessage = result.stderr.decode()
        if not errmessage.startswith('No files exist. Exiting.'):
            log.error('Failed to get {} task log for task_id={}: {}'.format(task_file, task_id, errmessage))
        return None
    return result.stdout.decode()


@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    """
    See: https://docs.pytest.org/en/latest/example/simple.html\
    #making-test-result-information-available-in-fixtures
    """
    # execute all other hooks to obtain the report object, then a report
    # attribute for each phase of a call, which can be "setup", "call", "teardown"
    # Subsequent fixtures can get the reports off of the request object like:
    # `request.rep_setup.failed`.
    outcome = yield
    rep = outcome.get_result()
    setattr(item, "rep_" + rep.when, rep)

    # Handle failures. Must be done here and not in a fixture in order to
    # properly handle post-yield fixture teardown failures.
    if rep.failed:
        log.error('Test {} failed in {} phase, dumping state'.format(item.name, rep.when))
        try:
            get_task_logs_on_failure(item.name)
        except Exception:
            log.exception('Task log collection failed!')
        try:
            get_mesos_state_on_failure(item.name)
        except Exception:
            log.exception('Mesos state collection failed!')


def get_rotating_task_log_lines(task_id: str, task_file: str):
    rotated_filenames = [task_file,]
    rotated_filenames.extend(['{}.{}'.format(task_file, i) for i in range(1, 10)])
    for filename in rotated_filenames:
        lines = get_task_logs_for_id(task_id, filename)
        if not lines:
            return
        yield filename, lines


def get_task_logs_on_failure(test_name: str):
    for task_id in get_task_ids():
        for task_file in ('stderr', 'stdout'):
            for log_filename, log_lines in get_rotating_task_log_lines(task_id, task_file):
                log_name = '{}_{}_{}.log'.format(test_name, task_id, log_filename)
                with open(log_name, 'w') as f:
                    f.write(log_lines)


def get_mesos_state_on_failure(test_name: str):
    dcosurl, headers = sdk_security.get_dcos_credentials()
    state_json_endpoint = '{}/mesos/state.json'.format(dcosurl)
    r = requests.get(state_json_endpoint, headers=headers, verify=False)
    if r.status_code == 200:
        log_name = '{}_state.json'.format(test_name)
        with open(log_name, 'w') as f:
            f.write(r.text)
    slaves_endpoint = '{}/mesos/slaves'.format(dcosurl)
    r = requests.get(slaves_endpoint, headers=headers, verify=False)
    if r.status_code == 200:
        log_name = '{}_slaves.json'.format(test_name)
        with open(log_name, 'w') as f:
            f.write(r.text)
