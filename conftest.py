""" This file configures python logging for the pytest framework
integration tests

Note: pytest must be invoked with this file in the working directory
E.G. py.test frameworks/<your-frameworks>/tests
"""
import logging
import os
import os.path
import shutil
import subprocess
import sys

import pytest
import requests
import sdk_security
import sdk_utils

log_level = os.getenv('TEST_LOG_LEVEL', 'INFO').upper()
log_levels = ('DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL', 'EXCEPTION')
assert log_level in log_levels, \
    '{} is not a valid log level. Use one of: {}'.format(log_level, ', '.join(log_levels))
# write everything to stdout due to the following circumstances:
# - shakedown uses print() aka stdout
# - teamcity splits out stdout vs stderr into separate outputs, we'd want them combined
logging.basicConfig(
    format='[%(asctime)s|%(name)s|%(levelname)s]: %(message)s',
    level=log_level,
    stream=sys.stdout)

# reduce excessive DEBUG/INFO noise produced by some underlying libraries:
for noise_source in [
        'dcos.http',
        'dcos.marathon',
        'dcos.util',
        'paramiko.transport',
        'urllib3.connectionpool']:
    logging.getLogger(noise_source).setLevel('WARNING')

log = logging.getLogger(__name__)

prior_task_ids = set([])


def get_task_ids():
    """ This function uses dcos task WITHOUT the JSON options because
    that can return the wrong user for schedulers
    """
    tasks = subprocess.check_output(['dcos', 'task', '--all']).decode().split('\n')
    for task_str in tasks[1:]:  # First line is the header line
        task = task_str.split()
        if len(task) < 5:
            continue
        yield task[4]


def get_task_files_for_id(task_id: str):
    return set(subprocess.check_output(['dcos', 'task', 'ls', task_id, '--all']).decode().split())


def get_task_logs_for_id(task_id: str,  task_file: str='stdout', lines: int=1000000):
    log.info("Fetching {} from {}".format(task_file, task_id))
    result = subprocess.run(
        ['dcos', 'task', 'log', task_id, '--all', '--lines', str(lines), task_file],
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

    # Update the list of task ids that have been seen, regardless of this test's outcome.
    global prior_task_ids
    new_task_ids = [id for id in get_task_ids() if id not in prior_task_ids]
    prior_task_ids = prior_task_ids.union(new_task_ids)

    # Handle failures. Must be done here and not in a fixture in order to
    # properly handle post-yield fixture teardown failures.
    if rep.failed:
        # fetch logs of only those tasks that were created during the test

        log.info('Test {} failed in {} phase. Dumping mesos state, and stdout/stderr logs for {} tasks: {}'.format(
            item.name, rep.when, len(new_task_ids), new_task_ids))

        # delete any preexisting log directory from a prior run of this test,
        # to avoid overlapping mixed logs from two different tests
        test_log_dir = sdk_utils.get_test_log_directory(item)
        if os.path.exists(test_log_dir):
            shutil.rmtree(test_log_dir)

        try:
            get_task_logs_on_failure(item, new_task_ids)
        except Exception:
            log.exception('Task log collection failed!')
        try:
            get_mesos_state_on_failure(item)
        except Exception:
            log.exception('Mesos state collection failed!')


def pytest_runtest_setup(item):
    # Initialize the list of task ids to include any entries unrelated to this run.
    global prior_task_ids
    prior_task_ids = prior_task_ids.union(get_task_ids())

    min_version_mark = item.get_marker('dcos_min_version')
    if min_version_mark:
        min_version = min_version_mark.args[0]
        message = 'Feature only supported in DC/OS {} and up'.format(min_version)
        if 'reason' in min_version_mark.kwargs:
            message += ': {}'.format(min_version_mark.kwargs['reason'])
        if sdk_utils.dcos_version_less_than(min_version):
            pytest.skip(message)


def setup_artifact_path(item: pytest.Item, artifact_name: str):
    '''Given the pytest item and an artifact_name,
    Returns the path to write an artifact with that name.'''
    output_dir = sdk_utils.get_test_log_directory(item)
    if not os.path.isdir(output_dir):
        os.makedirs(output_dir)
    return os.path.join(output_dir, artifact_name)


def get_rotating_task_log_lines(task_id: str, known_task_files: set, task_file: str):
    rotated_filenames = [task_file, ]
    rotated_filenames.extend(['{}.{}'.format(task_file, i) for i in range(1, 10)])
    for filename in rotated_filenames:
        if not filename in known_task_files:
            return # hit an index that doesn't exist, exit early
        lines = get_task_logs_for_id(task_id, filename)
        if not lines:
            log.error('Unable to fetch content of {} from task {}, giving up'.format(filename, task_id))
            return
        yield filename, lines


def get_task_logs_on_failure(item: pytest.Item, task_ids: list):
    for task_id in task_ids:
        # get list of available files:
        known_task_files = get_task_files_for_id(task_id)
        for task_file in ('stderr', 'stdout'):
            for log_filename, log_lines in get_rotating_task_log_lines(task_id, known_task_files, task_file):
                with open(setup_artifact_path(item, '{}.{}'.format(task_id, log_filename)), 'w') as f:
                    f.write(log_lines)


def get_mesos_state_on_failure(item: pytest.Item):
    dcosurl, headers = sdk_security.get_dcos_credentials()
    for name in ['state.json', 'slaves']:
        r = requests.get('{}/mesos/{}'.format(dcosurl, name), headers=headers, verify=False)
        if r.status_code == 200:
            if name.endswith('.json'):
                name = name[:-len('.json')] # avoid duplicate '.json'
            with open(setup_artifact_path(item, 'mesos_{}.json'.format(name)), 'w') as f:
                f.write(r.text)
