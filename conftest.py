""" This file configures python logging for the pytest framework
integration tests

Note: pytest must be invoked with this file in the working directory
E.G. py.test frameworks/<your-frameworks>/tests
"""
import logging
import os
import subprocess

import pytest

log_level = os.getenv('TEST_LOG_LEVEL', 'INFO').upper()

log_levels = (
    'DEBUG',
    'INFO',
    'WARNING',
    'ERROR',
    'CRITICAL',
    'EXCEPTION')

assert log_level in log_levels, '{} is not a valid log level. ' \
    'Use one of: {}'.format(log_level, ', '.join(log_levels))

logging.basicConfig(
    format='[%(asctime)s|%(name)s|%(levelname)s]: %(message)s',
    level=log_level)


def get_task_ids_for_user(user: str):
    """ This function uses dcos task WITHOUT the JSON options because
    that can return the wrong user for schedulers
    """
    tasks = subprocess.check_output(['dcos', 'task']).decode().split('\n')
    for task_str in tasks:
        task = task_str.split()
        if len(task) < 5:
            continue
        if task[2] == user:
            yield task[4]


def get_task_logs_for_id(task_id: str, lines: int=1000000):
    return subprocess.check_output([
        'dcos', 'task', 'log', task_id, '--lines', str(lines)]).decode()


@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    """
    See: https://docs.pytest.org/en/latest/example/simple.html\
    #making-test-result-information-available-in-fixtures
    """
    # execute all other hooks to obtain the report object
    outcome = yield
    rep = outcome.get_result()

    # set a report attribute for each phase of a call, which can
    # be "setup", "call", "teardown"

    setattr(item, "rep_" + rep.when, rep)


@pytest.fixture(autouse=True)
def get_scheduler_logs_on_failure(request):
    """ Scheduler should be the only task running as root
    """
    yield
    for report in ('rep_setup', 'rep_call', 'rep_teardown'):
        if not hasattr(request.node, report):
            continue
        if not getattr(request.node, report).failed:
            continue
        # Scheduler should be the only task running as root
        for root_task in get_task_ids_for_user('root'):
            log_name = '{}_{}.log'.format(request.node.name, root_task)
            with open(log_name, 'w') as f:
                f.write(get_task_logs_for_id(root_task))
