""" This file configures python logging for the pytest framework
integration tests

Note: pytest must be invoked with this file in the working directory
E.G. py.test frameworks/<your-frameworks>/tests
"""
import collections
import json
import logging
import os
import os.path
import re
import retrying
import shutil
import sys
import time
import traceback

import pytest
import sdk_cmd
import sdk_tasks
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
        new_task_ids = [task.id for task in sdk_tasks.get_summary(with_completed=True)
                        if task.id not in testlogs_ignored_task_ids]
        testlogs_ignored_task_ids = testlogs_ignored_task_ids.union(new_task_ids)
        # Enforce limit on how many tasks we will fetch logs from, to avoid unbounded log fetching.
        if len(new_task_ids) > testlogs_task_id_limit:
            log.warning('Truncating list of {} new tasks to size {} to avoid fetching logs forever: {}'.format(
                len(new_task_ids), testlogs_task_id_limit, new_task_ids))
            del new_task_ids[testlogs_task_id_limit:]
        log.info('Test {} failed in {} phase.'.format(item.name, rep.when))

        try:
            log.info('Fetching logs for {} tasks launched in this suite since last failure: {}'.format(
                len(new_task_ids), ', '.join(new_task_ids)))
            dump_relevant_task_logs(item, new_task_ids)
        except:
            log.exception('Task log collection failed!')
        try:
            log.info('Fetching mesos state')
            dump_mesos_state(item)
        except:
            log.exception('Mesos state collection failed!')
        try:
            log.info('Creating/fetching cluster diagnostics bundle')
            get_diagnostics_bundle(item)
        except:
            log.exception("Diagnostics bundle creation failed")
        log.info('Post-failure collection complete')


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
        testlogs_ignored_task_ids = testlogs_ignored_task_ids.union([
            task.id for task in sdk_tasks.get_summary(with_completed=True)])
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


class TaskEntry(object):
    def __init__(self, cluster_task):
        self.task_id = cluster_task['id']
        self.executor_id = cluster_task['executor_id']
        self.agent_id = cluster_task['slave_id']


    def __repr__(self):
        return 'Task[task_id={} executor_id={} agent_id={}]'.format(
            self.task_id, self.executor_id, self.agent_id)


def dump_relevant_task_logs_for_task(item: pytest.Item, agent_id: str, agent_executor_paths: dict, task_entry: TaskEntry):
    # get directories to be browsed
    task_browse_paths = []
    for browse_path in agent_executor_paths.keys():
        # try executor_id if non-empty, else task_id.
        # marathon/metronome apps will have an empty executor_id, in which case the task_id should be used.
        if browse_path.startswith('/frameworks') and \
           browse_path.endswith('/executors/{}/runs/latest'.format(
               task_entry.executor_id if task_entry.executor_id else task_entry.task_id)):
            task_browse_paths.append(browse_path)
            if task_entry.executor_id:
                # when executor_id and task_id are (both) present, then also browse task path:
                #   /frameworks/.../executors/<executor_id>/runs/latest/tasks/<task_id>/
                task_browse_paths.append(os.path.join(browse_path, 'tasks/{}/'.format(task_entry.task_id)))
    if not task_browse_paths:
        # did mesos move their files around?
        log.warning('Unable to find any paths matching task {} in agent {}:\n  {}'.format(
            task_entry, agent_id, '\n  '.join(sorted(agent_executor_paths.keys()))))
        return

    log.info('Searching {} paths for task {}:\n  {}'.format(
        len(task_browse_paths), task_entry, '\n  '.join(sorted(task_browse_paths))))

    # browse directories, select files to be fetched
    selected_file_infos = collections.OrderedDict()
    for task_browse_path in task_browse_paths:
        file_infos = sdk_cmd.cluster_request(
            'GET', '/slave/{}/files/browse?path={}'.format(agent_id, task_browse_path)).json()
        # select files named 'stdout[.##]' and 'stderr[.##]'
        for file_info in file_infos:
            if not re.match('^.*/(stdout|stderr)(\.[0-9]+)?$', file_info['path']):
                continue
            # Output filename (sort by time):
            # 180125_225944.world-1-server__4d534510-35d9-4f06-811e-e9a9ffa4d14f.task.stdout
            # 180126_000225.hello-0-server__15174696-2d3d-48e9-b492-d9a0cc289786.executor.stderr
            # 180126_002024.hello-world.662e7976-0224-11e8-b2f2-deead5f2b92b.stdout.1
            if task_entry.executor_id:
                # file may be from the task, or from the executor
                source = 'task.' if '/tasks/' in file_info['path'] else 'executor.'
            else:
                # there aren't separate task/executor logs, so omit source
                source = ''
            out_filename = '{}.{}.{}{}'.format(
                time.strftime('%y%m%d_%H%M%S', time.gmtime(file_info['mtime'])),
                task_entry.task_id,
                source,
                os.path.basename(file_info['path']))
            selected_file_infos[setup_artifact_path(item, out_filename)] = file_info

    if not selected_file_infos:
        log.warning('Unable to find any stdout/stderr files in above paths for task {}'.format(task_entry))
        return
    byte_count = sum([f['size'] for f in selected_file_infos.values()])
    log.info('Downloading {} files ({} bytes) for task {}:{}'.format(
        len(selected_file_infos),
        byte_count,
        task_entry,
        ''.join(['\n  {} ({} bytes)\n    => {}'.format(
            file_info['path'], file_info['size'], path) for path, file_info in selected_file_infos.items()])))

    # fetch files
    for out_path, file_info in selected_file_infos.items():
        try:
            stream = sdk_cmd.cluster_request(
                'GET', '/slave/{}/files/download?path={}'.format(agent_id, file_info['path']), stream=True)
            with open(out_path, 'wb') as f:
                for chunk in stream.iter_content(chunk_size=8192):
                    f.write(chunk)
        except:
            log.exception('Failed to get file for task {}: {}'.format(task_entry, file_info))
    return byte_count


def dump_relevant_task_logs_for_agent(item: pytest.Item, agent_id: str, agent_tasks: list):
    agent_executor_paths = sdk_cmd.cluster_request('GET', '/slave/{}/files/debug'.format(agent_id)).json()
    task_byte_count = 0
    for task_entry in agent_tasks:
        try:
            task_byte_count += dump_relevant_task_logs_for_task(item, agent_id, agent_executor_paths, task_entry)
        except:
            log.exception('Failed to get logs for task {}'.format(task_entry))
    log.info('Downloaded {} bytes of logs from {} tasks on agent {}'.format(
        task_byte_count, len(agent_tasks), agent_id))

    # fetch agent log separately due to its totally different fetch semantics vs the task/executor logs
    if '/slave/log' in agent_executor_paths:
        out_path = setup_artifact_path(item, 'agent_{}.log'.format(agent_id))
        stream = sdk_cmd.cluster_request(
            'GET', '/slave/{}/files/download?path=/slave/log'.format(agent_id), stream=True)
        with open(out_path, 'wb') as f:
            for chunk in stream.iter_content(chunk_size=8192):
                f.write(chunk)


def dump_relevant_task_logs(item: pytest.Item, task_ids: list):
    task_ids_set = set(task_ids)
    cluster_tasks = sdk_cmd.cluster_request('GET', '/mesos/tasks').json()
    matching_tasks_by_agent = {}
    for cluster_task in cluster_tasks['tasks']:
        task_entry = TaskEntry(cluster_task)
        if task_entry.task_id in task_ids_set:
            agent_tasks = matching_tasks_by_agent.get(task_entry.agent_id, [])
            agent_tasks.append(task_entry)
            matching_tasks_by_agent[task_entry.agent_id] = agent_tasks

    for agent_id, agent_tasks in matching_tasks_by_agent.items():
        try:
            dump_relevant_task_logs_for_agent(item, agent_id, agent_tasks)
        except:
            log.exception('Failed to get logs for agent {}'.format(agent_id))


def dump_mesos_state(item: pytest.Item):
    for name in ['state.json', 'slaves']:
        r = sdk_cmd.cluster_request('GET', '/mesos/{}'.format(name), verify=False, raise_on_error=False)
        if r.ok:
            if name.endswith('.json'):
                name = name[:-len('.json')] # avoid duplicate '.json'
            with open(setup_artifact_path(item, 'mesos_{}.json'.format(name)), 'w') as f:
                f.write(r.text)


def get_diagnostics_bundle(item: pytest.Item):
    rc, _, _ = sdk_cmd.run_raw_cli('node diagnostics create all')
    if rc:
        log.error('Diagnostics bundle creation failed.')
        return

    @retrying.retry(
        wait_fixed=5000,
        stop_max_delay=10*60*1000,
        retry_on_result=lambda result: result is None)
    def wait_for_bundle_file():
        rc, stdout, stderr = sdk_cmd.run_raw_cli('node diagnostics --status --json')
        if rc:
            return None

        # e.g. { "some-ip": { stuff we want } }
        status = next(iter(json.loads(stdout).values()))
        if status['job_progress_percentage'] != 100:
            return None

        # e.g. "/var/lib/dcos/dcos-diagnostics/diag-bundles/bundle-2018-01-11-1515698691.zip"
        return os.path.basename(status['last_bundle_dir'])

    bundle_filename = wait_for_bundle_file()
    if bundle_filename:
        sdk_cmd.run_cli('node diagnostics download {} --location={}'.format(
            bundle_filename, setup_artifact_path(item, bundle_filename)))
    else:
        log.error('Diagnostics bundle didnt finish in time, giving up.')
