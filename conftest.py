""" This file configures python logging for the pytest framework
integration tests

Note: pytest must be invoked with this file in the working directory
E.G. py.test frameworks/<your-frameworks>/tests
"""
import logging
import os
import os.path
import re
import shutil
import sys
import time

import pytest
import requests
import sdk_cmd
import sdk_security
import sdk_utils
import teamcity

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

# Regex pattern which parses the output of "dcos task log ls --long", in order to extract the filename and timestamp.
# Example inputs:
#   drwxr-xr-x  6  nobody  nobody      4096  Jul 21 22:07                             jre1.8.0_144
#   drwxr-xr-x  3  nobody  nobody      4096  Jun 28 12:50                          libmesos-bundle
#   -rw-r--r--  1  nobody  nobody  32539549  Jan 04 16:31  libmesos-bundle-1.10-1.4-63e0814.tar.gz
# Example output:
#   match.group(1): "4096  ", match.group(2): "Jul 21 22:07", match.group(3): "jre1.8.0_144  "
# Notes:
# - Should also support spaces in filenames.
# - Doesn't make any assumptions about the contents of the tokens before the timestamp/filename,
#   just assumes that there are 5 of them.
#                             TOKENS        MONTH     DAY    HH:MM    FILENAME
task_ls_pattern = re.compile('^([^ ]+ +){5}([a-zA-z]+ [0-9]+ [0-9:]+) +(.*)$')

# An arbitrary limit on the number of tasks that we fetch logs from following a failed test:
#     100 (task id limit)
#     2   (stdout + stderr file per task)
#   x ~4s (time to retrieve each file)
#   ---------------------
#   max ~13m20s to download logs upon test failure (plus any .1/.2/.../.9 logs)
testlogs_task_id_limit = 100

# Keep track of task ids to collect logs at the correct times. Example scenario:
# 1 Test suite test_sanity_py starts with 2 tasks to ignore: [test_placement-0, test_placement-1]
# 2 test_sanity_py.health_check passes, with 3 tasks created: [test-scheduler, pod-0-task, pod-1-task]
# 3 test_sanity_py.replace_0 fails, with 1 task created: [pod-0-task-NEWUUID]
#   Upon failure, the following task logs should be collected: [test-scheduler, pod-0-task, pod-1-task, pod-0-task-NEWUUID]
# 4 test_sanity_py.replace_1 succeeds, with 1 task created: [pod-1-task-NEWUUID]
# 5 test_sanity_py.restart_1 fails, with 1 new task: [pod-1-task-NEWUUID2]
#   Upon failure, the following task logs should be collected: [pod-1-task-NEWUUID, pod-1-task-NEWUUID2]
#   These are the tasks which were newly created following the prior failure.
#   Previously-collected tasks are not collected again, even though they may have additional log content.
#   In practice this is fine -- e.g. Scheduler would restart with a new task id if it was reconfigured anyway.

# The name of current test suite (e.g. 'test_sanity_py'), or an empty string if no test suite has
# started yet. This is used to determine when the test suite has changed in a test run.
testlogs_current_test_suite = ""

# The list of all task ids to ignore when fetching task logs in future test failures:
# - Task ids that already existed at the start of a test suite.
#   (ignore tasks unrelated to this test suite)
# - Task ids which have been logged following a prior failure in the current test suite.
#   (ignore task ids which were already collected before, even if there's new content)
testlogs_ignored_task_ids = set([])

# The index of the current test, which increases as tests are run, and resets when a new test suite
# is started. This is used to sort test logs in the order that they were executed, and is useful
# when tracing a chain of failed tests.
testlogs_test_index = 0


def get_task_ids():
    """ This function uses dcos task WITHOUT the JSON options because
    that can return the wrong user for schedulers
    """
    tasks = sdk_cmd.run_cli('task --all', print_output=False).split('\n')
    for task_str in tasks[1:]:  # First line is the header line
        task = task_str.split()
        if len(task) < 5:
            continue
        yield task[4]


@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    '''Hook to run after every test, before any other post-test hooks.
    See also: https://docs.pytest.org/en/latest/example/simple.html\
    #making-test-result-information-available-in-fixtures
    '''
    # Execute all other hooks to obtain the report object, then a report attribute for each phase of
    # a call, which can be "setup", "call", "teardown".
    # Subsequent fixtures can get the reports off of the request object like: `request.rep_setup.failed`.
    outcome = yield
    rep = outcome.get_result()
    setattr(item, "rep_" + rep.when, rep)

    # Handle failures. Must be done here and not in a fixture in order to
    # properly handle post-yield fixture teardown failures.
    if rep.failed:
        # Fetch all logs from tasks created since the last failure, or since the start of the suite.
        global testlogs_ignored_task_ids
        new_task_ids = [id for id in get_task_ids() if id not in testlogs_ignored_task_ids]
        testlogs_ignored_task_ids = testlogs_ignored_task_ids.union(new_task_ids)
        # Enforce limit on how many tasks we will fetch logs from, to avoid unbounded log fetching.
        if len(new_task_ids) > testlogs_task_id_limit:
            log.warning('Truncating list of {} new tasks to size {} to avoid fetching logs forever: {}'.format(
                len(new_task_ids), testlogs_task_id_limit, new_task_ids))
            del new_task_ids[testlogs_task_id_limit:]
        log.info('Test {} failed in {} phase.'.format(item.name, rep.when))

        try:
            log.info('Dumping logs for {} tasks launched in this suite since last failure: {}'.format(
                len(new_task_ids), new_task_ids))
            dump_task_logs(item, new_task_ids)
        except Exception:
            log.exception('Task log collection failed!')
        try:
            dump_mesos_state(item)
        except Exception:
            log.exception('Mesos state collection failed!')
        try:
            get_diagnostics_bundle(item)
        except Exception:
            log.exception("Diagnostics bundle creation failed")


def pytest_runtest_teardown(item):
    '''Hook to run after every test.'''
    # Inject footer at end of test, may be followed by additional teardown.
    # Don't do this when running in teamcity, where it's redundant.
    if not teamcity.is_running_under_teamcity():
        print('''
==========
======= END: {}::{}
=========='''.format(sdk_utils.get_test_suite_name(item), item.name))


def pytest_runtest_setup(item):
    '''Hook to run before every test.'''
    # Inject header at start of test, following automatic "path/to/test_file.py::test_name":
    # Don't do this when running in teamcity, where it's redundant.
    if not teamcity.is_running_under_teamcity():
        print('''
==========
======= START: {}::{}
=========='''.format(sdk_utils.get_test_suite_name(item), item.name))

    # Check if we're entering a new test suite.
    global testlogs_test_index
    global testlogs_current_test_suite
    test_suite = sdk_utils.get_test_suite_name(item)
    if test_suite != testlogs_current_test_suite:
        # New test suite:
        # 1 Store all the task ids which already exist as of this point.
        testlogs_current_test_suite = test_suite
        global testlogs_ignored_task_ids
        testlogs_ignored_task_ids = testlogs_ignored_task_ids.union(get_task_ids())
        log.info('Entering new test suite {}: {} preexisting tasks will be ignored on test failure.'.format(
            test_suite, len(testlogs_ignored_task_ids)))
        # 2 Reset the test index.
        testlogs_test_index = 0
        # 3 Remove any prior logs for the test suite.
        test_log_dir = sdk_utils.get_test_suite_log_directory(item)
        if os.path.exists(test_log_dir):
            log.info('Deleting existing test suite logs: {}/'.format(test_log_dir))
            shutil.rmtree(test_log_dir)

    # Increment the test index (to 1, if this is a new suite), and pass the value to sdk_utils for use internally.
    testlogs_test_index += 1
    sdk_utils.set_test_index(testlogs_test_index)

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


def get_task_files_for_id(task_id: str) -> dict:
    try:
        ls_lines = sdk_cmd.run_cli('task ls --long --all {}'.format(task_id)).split('\n')
        ret = {}
        for line in ls_lines:
            match = task_ls_pattern.match(line)
            if not match:
                log.warning('Unable to parse line: {}'.format(line))
                continue
            # match.group(1): "4096  ", match.group(2): "Jul 21 22:07", match.group(3): "jre1.8.0_144  "
            filename = match.group(3).strip()
            # build timestamp for use in output filename: 'Jul 21 22:07' => '0721_2207'
            timestamp = time.strftime('%m%d_%H%M', time.strptime(match.group(2), '%b %d %H:%M'))
            ret[filename] = timestamp
        return ret
    except:
        log.exception('Failed to get list of files for task: {}'.format(task_id))
        return {}


def get_task_log_for_id(task_id: str,  task_file: str='stdout', lines: int=1000000) -> str:
    log.info('Fetching {} from {}'.format(task_file, task_id))
    rc, stdout, stderr = sdk_cmd.run_raw_cli('task log {} --all --lines {} {}'.format(task_id, lines, task_file), print_output=False)
    if rc != 0:
        if not stderr.startswith('No files exist. Exiting.'):
            log.error('Failed to get {} task log for task_id={}: {}'.format(task_file, task_id, stderr))
        return ''
    return stdout


def get_rotating_task_logs(task_id: str, task_file_timestamps: dict, task_file: str):
    rotated_filenames = [task_file, ]
    rotated_filenames.extend(['{}.{}'.format(task_file, i) for i in range(1, 10)])
    for filename in rotated_filenames:
        if not filename in task_file_timestamps:
            return # Reached a log index that doesn't exist, exit early
        content = get_task_log_for_id(task_id, filename)
        if not content:
            log.error('Unable to fetch content of {} from task {}, giving up'.format(filename, task_id))
            return
        yield filename, task_file_timestamps[filename], content


def dump_task_logs(item: pytest.Item, task_ids: list):
    for task_id in task_ids:
        # Get list of available files:
        task_file_timestamps = get_task_files_for_id(task_id)
        for task_file in ('stdout', 'stderr'):
            for log_filename, log_timestamp, log_content in get_rotating_task_logs(task_id, task_file_timestamps, task_file):
                # output filename (sort by time): '0104_1709.hello-world.0fe39302-f18b-11e7-a6f9-ae11b3b25138.stdout'
                out_path = setup_artifact_path(item, '{}.{}.{}'.format(log_timestamp, task_id, log_filename))
                log.info('=> Writing {} ({} bytes)'.format(out_path, len(log_content)))
                with open(out_path, 'w') as f:
                    f.write(log_content)


def dump_mesos_state(item: pytest.Item):
    dcosurl, headers = sdk_security.get_dcos_credentials()
    for name in ['state.json', 'slaves']:
        r = requests.get('{}/mesos/{}'.format(dcosurl, name), headers=headers, verify=False)
        if r.status_code == 200:
            if name.endswith('.json'):
                name = name[:-len('.json')] # avoid duplicate '.json'
            with open(setup_artifact_path(item, 'mesos_{}.json'.format(name)), 'w') as f:
                f.write(r.text)


def get_diagnostics_bundle(item: pytest.Item):
    result = subprocess.run(
        ['dcos', 'node', 'diagnostics', 'create', 'all'], stdout=subprocess.PIPE, stderr=subprocess.PIPE
    )
    if result.returncode:
        errmessage = result.stderr.decode()
        log.error("Couldn't create diagnostics bundle: {}".format(errmessage))

    bundle_file = None
    while bundle_file is None:
        status = subprocess.run(
            ['dcos', 'node', 'diagnostics', '--status'], stdout=subprocess.PIPE, stderr=subprocess.PIPE
        )
        progress = [
            l.strip() for l in status.stdout.decode().split('\n') if 'job_progress_percentage' in l
        ][0]

        if progress.endswith('100'):
            bundle_file = [
                l.strip().split('/')[-1] for l in status.stdout.decode().split('\n')
                if 'last_bundle_dir' in l
            ][0]

    original_cwd = os.getcwd()
    try:
        os.chdir(setup_artifact_path(item, '').rstrip('/'))
        subprocess.run(['dcos', 'node', 'diagnostics', 'download', bundle_file])
    finally:
        os.chdir(original_cwd)
