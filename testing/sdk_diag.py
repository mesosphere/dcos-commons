'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_diag IN ANY OTHER PARTNER REPOS
************************************************************************
'''

import collections
import json
import logging
import os.path
import re
import shutil
import time

import pytest
import retrying

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_tasks

log = logging.getLogger(__name__)


# An arbitrary limit on the number of tasks that we fetch logs from following a failed test.
# Ideally this should be scaled to the number of tasks that can be fetched within ~10min.
_testlogs_task_id_limit = 250

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
_testlogs_current_test_suite = ''

# The list of all task ids to ignore when fetching task logs in future test failures:
# - Task ids that already existed at the start of a test suite.
#   (ignore tasks unrelated to this test suite)
# - Task ids which have been logged following a prior failure in the current test suite.
#   (ignore task ids which were already collected before, even if there's new content)
_testlogs_ignored_task_ids = set([])

# The index of the current test, which increases as tests are run, and resets when a new test suite
# is started. This is used to sort test logs in the order that they were executed, and is useful
# when tracing a chain of failed tests.
_testlogs_test_index = 0


def get_test_suite_name(item: pytest.Item):
    '''Returns the test suite name to use for a given test.'''
    # frameworks/template/tests/test_sanity.py => test_sanity_py
    # tests/test_sanity.py => test_sanity_py
    return os.path.basename(item.parent.name).replace('.','_')


def handle_test_setup(item: pytest.Item):
    '''Does some initialization at the start of a test.

    This should be called in a pytest_runtest_setup() hook.
    See also handle_failed_test() which must be called from a pytest_runtest_makereport() hookimpl hook.'''

    # Check if we're entering a new test suite.
    global _testlogs_test_index
    global _testlogs_current_test_suite
    test_suite = get_test_suite_name(item)
    if test_suite != _testlogs_current_test_suite:
        # New test suite:
        # 1 Store all the task ids which already exist as of this point.
        _testlogs_current_test_suite = test_suite
        global _testlogs_ignored_task_ids
        _testlogs_ignored_task_ids = _testlogs_ignored_task_ids.union([
            task.id for task in sdk_tasks.get_summary(with_completed=True)])
        log.info('Entering new test suite {}: {} preexisting tasks will be ignored on test failure.'.format(
            test_suite, len(_testlogs_ignored_task_ids)))
        # 2 Reset the test index.
        _testlogs_test_index = 0
        # 3 Remove any prior logs for the test suite.
        test_log_dir = _test_suite_artifact_directory(item)
        if os.path.exists(test_log_dir):
            log.info('Deleting existing test suite logs: {}/'.format(test_log_dir))
            shutil.rmtree(test_log_dir)

    # Increment the test index (to 1, if this is a new suite)
    _testlogs_test_index += 1


def handle_test_report(item: pytest.Item, result): # _pytest.runner.TestReport
    '''Collects information from the cluster following a failed test.

    This should be called in a hookimpl fixture.
    See also handle_test_setup() which must be called in a pytest_runtest_setup() hook.'''

    if not result.failed:
        return # passed, nothing to do

    # Fetch all plans from all currently-installed services.
    # We do this retrieval first in order to be closer to the actual test failure.
    # Services may still be installed when e.g. we're still in the middle of a test suite.
    service_names = sdk_install.get_installed_service_names()
    if len(service_names) > 0:
        log.info('Fetching plans for {} services that are currently installed: {}'.format(
            len(service_names), ', '.join(service_names)))
        for service_name in service_names:
            try:
                _dump_plans(item, service_name)
            except:
                log.exception('Plan collection from service {} failed!'.format(service_name))

    # Fetch all logs from tasks created since the last failure, or since the start of the suite.
    global _testlogs_ignored_task_ids
    new_task_ids = [task.id for task in sdk_tasks.get_summary(with_completed=True)
                    if task.id not in _testlogs_ignored_task_ids]
    _testlogs_ignored_task_ids = _testlogs_ignored_task_ids.union(new_task_ids)
    # Enforce limit on how many tasks we will fetch logs from, to avoid unbounded log fetching.
    if len(new_task_ids) > _testlogs_task_id_limit:
        log.warning('Truncating list of {} new tasks to size {} to avoid fetching logs forever: {}'.format(
            len(new_task_ids), _testlogs_task_id_limit, new_task_ids))
        del new_task_ids[_testlogs_task_id_limit:]
    try:
        log.info('Fetching logs for {} tasks launched in this suite since last failure: {}'.format(
            len(new_task_ids), ', '.join(new_task_ids)))
        _dump_task_logs(item, new_task_ids)
    except:
        log.exception('Task log collection failed!')
    try:
        log.info('Fetching mesos state:')
        _dump_mesos_state(item)
    except:
        log.exception('Mesos state collection failed!')
    try:
        log.info('Creating/fetching cluster diagnostics bundle:')
        _dump_diagnostics_bundle(item)
    except:
        log.exception('Diagnostics bundle creation failed')
    log.info('Post-failure collection complete')


def _dump_plans(item: pytest.Item, service_name: str):
    '''If the test had failed, writes the plan state(s) to log file(s).'''

    # Use brief timeouts, we just want a best-effort attempt here:
    plan_names = sdk_plan.list_plans(service_name, 5)
    for plan_name in plan_names:
        plan = sdk_plan.get_plan(service_name, plan_name, 5)
        # Include service name in plan filename, but be careful about folders...
        out_path = _setup_artifact_path(item, 'plan_{}_{}.json'.format(service_name.replace('/', '_'), plan_name))
        out_content = json.dumps(plan, indent=2)
        log.info('=> Writing {} ({} bytes)'.format(out_path, len(out_content)))
        with open(out_path, 'w') as f:
            f.write(out_content)
            f.write('\n') # ... and a trailing newline


def _dump_diagnostics_bundle(item: pytest.Item):
    '''Creates and downloads a DC/OS diagnostics bundle, and saves it to the artifact path for this test.'''
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
            bundle_filename, _setup_artifact_path(item, bundle_filename)))
    else:
        log.error('Diagnostics bundle didnt finish in time, giving up.')


def _dump_mesos_state(item: pytest.Item):
    '''Downloads state from the Mesos master and saves it to the artifact path for this test.'''
    for name in ['state.json', 'slaves']:
        r = sdk_cmd.cluster_request('GET', '/mesos/{}'.format(name), verify=False, raise_on_error=False)
        if r.ok:
            if name.endswith('.json'):
                name = name[:-len('.json')] # avoid duplicate '.json'
            with open(_setup_artifact_path(item, 'mesos_{}.json'.format(name)), 'w') as f:
                f.write(r.text)


def _dump_task_logs(item: pytest.Item, task_ids: list):
    '''For all of the provided tasks, downloads their task, executor, and agent logs to the artifact path for this test.'''
    task_ids_set = set(task_ids)
    cluster_tasks = sdk_cmd.cluster_request('GET', '/mesos/tasks').json()
    matching_tasks_by_agent = {}
    for cluster_task in cluster_tasks['tasks']:
        task_entry = _TaskEntry(cluster_task)
        if task_entry.task_id in task_ids_set:
            agent_tasks = matching_tasks_by_agent.get(task_entry.agent_id, [])
            agent_tasks.append(task_entry)
            matching_tasks_by_agent[task_entry.agent_id] = agent_tasks

    for agent_id, agent_tasks in matching_tasks_by_agent.items():
        try:
            _dump_task_logs_for_agent(item, agent_id, agent_tasks)
        except:
            log.exception('Failed to get logs for agent {}'.format(agent_id))


class _TaskEntry(object):
    def __init__(self, cluster_task):
        self.task_id = cluster_task['id']
        self.executor_id = cluster_task['executor_id']
        self.agent_id = cluster_task['slave_id']


    def __repr__(self):
        return 'Task[task_id={} executor_id={} agent_id={}]'.format(
            self.task_id, self.executor_id, self.agent_id)


def _dump_task_logs_for_agent(item: pytest.Item, agent_id: str, agent_tasks: list):
    agent_executor_paths = sdk_cmd.cluster_request('GET', '/slave/{}/files/debug'.format(agent_id)).json()
    task_byte_count = 0
    for task_entry in agent_tasks:
        try:
            task_byte_count += _dump_task_logs_for_task(item, agent_id, agent_executor_paths, task_entry)
        except:
            log.exception('Failed to get logs for task {}'.format(task_entry))
    log.info('Downloaded {} bytes of logs from {} tasks on agent {}'.format(
        task_byte_count, len(agent_tasks), agent_id))

    # fetch agent log separately due to its totally different fetch semantics vs the task/executor logs
    if '/slave/log' in agent_executor_paths:
        out_path = _setup_artifact_path(item, 'agent_{}.log'.format(agent_id))
        stream = sdk_cmd.cluster_request(
            'GET', '/slave/{}/files/download?path=/slave/log'.format(agent_id), stream=True)
        with open(out_path, 'wb') as f:
            for chunk in stream.iter_content(chunk_size=8192):
                f.write(chunk)


def _dump_task_logs_for_task(item: pytest.Item, agent_id: str, agent_executor_paths: dict, task_entry: _TaskEntry):
    executor_browse_path = _find_matching_executor_path(agent_executor_paths, task_entry)
    if not executor_browse_path:
        # Expected executor path was not found on this agent. Did Mesos move their files around again?
        log.warning('Unable to find any paths matching task {} in agent {}:\n  {}'.format(
            task_entry, agent_id, '\n  '.join(sorted(agent_executor_paths.keys()))))
        return

    # Fetch paths under the executor.
    executor_file_infos = sdk_cmd.cluster_request(
        'GET', '/slave/{}/files/browse?path={}'.format(agent_id, executor_browse_path)).json()

    # Look at the executor's sandbox and check for a 'tasks/' directory.
    # If it has one (due to being a Default Executor), then also fetch file infos for <executor_path>/tasks/<task_id>/
    task_file_infos = []
    if task_entry.executor_id and task_entry.task_id:
        for file_info in executor_file_infos:
            if file_info['mode'].startswith('d') and file_info['path'].endswith('/tasks'):
                task_browse_path = os.path.join(executor_browse_path, 'tasks/{}/'.format(task_entry.task_id))
                try:
                    task_file_infos = sdk_cmd.cluster_request(
                        'GET', '/slave/{}/files/browse?path={}'.format(agent_id, task_browse_path)).json()
                except:
                    log.exception('Failed to fetch task sandbox from presumed default executor')

    # Select all log files to be fetched from the above list.
    selected_file_infos = collections.OrderedDict()
    if task_file_infos:
        # Include 'task' and 'executor' annotations in filenames to differentiate between them:
        _select_log_files(item, task_entry.task_id, executor_file_infos, 'executor.', selected_file_infos)
        _select_log_files(item, task_entry.task_id, task_file_infos, 'task.', selected_file_infos)
    else:
        # No annotation needed:
        _select_log_files(item, task_entry.task_id, executor_file_infos, '', selected_file_infos)
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

    # Fetch files
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


def _find_matching_executor_path(agent_executor_paths: dict, task_entry: _TaskEntry) -> str:
    '''Finds and returns the executor directory for the provided task on the agent.

    Mesos has changed its schema for executor directories with each DC/OS release:
    - 1.9: There are only '/var/lib/mesos/...' paths. There are no '/runs/latest' paths, only '/runs/<UUID>'.
    - 1.10: There are only '/var/lib/mesos/...' paths, but '/runs/latest' paths are available in addition to '/runs/<UUID>'.
    - 1.11: There are both '/frameworks/...' paths and '/var/lib/mesos/...' paths. Both have '/runs/latest' as well as '/runs/<UUID>'.
    (and Mesos folks tell me that '/frameworks/...' is the way forward, so '/var/lib/mesos/...' may be going away)
    SEE ALSO: https://issues.apache.org/jira/browse/MESOS-7899

    Additionally, given the correct path, there are also differences depending on the task/executor type:
    - Marathon/Metronome: The task id is used as the 'executor id'. Logs are at the advertised directory.
    - Custom executor: 'executor id' + 'task id' are both used. Executor+Task logs are all combined into the same file(s) at the advertised directory.
    - Default executor: 'executor id' + 'task id' are both used. Executor logs are at the advertised directory,
                        while task logs are under 'tasks/<task_id>/' relative to the advertised directory.
    '''

    # When executor_id is empty (as in Marathon/Metronome tasks), we use the task_id:
    path_id = task_entry.executor_id if task_entry.executor_id else task_entry.task_id

    # - 1.11: '/frameworks/.../executors/<executor_id>/runs/latest'
    # Metronome: /frameworks/a31a2d3d-76a2-4d4b-82a3-a7e70e02c69c-0000/executors/test_cassandra_delete-data-retry_20180125024336zu3iM.8a893b4a-0179-11e8-ba9e-ee0228673934/runs/latest
    # Marathon: /frameworks/a31a2d3d-76a2-4d4b-82a3-a7e70e02c69c-0001/executors/test_integration_cassandra.57705baf-0176-11e8-94e4-ee0228673934/runs/latest
    # Default Executor: /frameworks/a31a2d3d-76a2-4d4b-82a3-a7e70e02c69c-0002/executors/node__bfa9751b-b7c4-45ae-b6d3-efdb9f851ca7/runs/latest
    #                   (executor logs here. tasks are then under .../tasks/<task_id>/)
    frameworks_latest_pattern = re.compile('^/frameworks/.*/executors/{}/runs/latest$'.format(path_id))
    for browse_path in agent_executor_paths.keys():
        if frameworks_latest_pattern.match(browse_path):
            return browse_path
    # - 1.10: '/var/lib/mesos/.../executors/<executor_id>/runs/latest'
    # Marathon: /var/lib/mesos/slave/slaves/6354b62c-7200-4458-8d7d-0dd11b281743-S1/frameworks/6354b62c-7200-4458-8d7d-0dd11b281743-0001/executors/hello-world.a80b075e-02d3-11e8-aceb-e2e215e145ce/runs/latest
    # Default Executor: /var/lib/mesos/slave/slaves/6354b62c-7200-4458-8d7d-0dd11b281743-S1/frameworks/6354b62c-7200-4458-8d7d-0dd11b281743-0002/executors/hello__090b3ef4-27c3-44c7-a39a-bad65620b982/runs/latest
    #                   (executor logs here. tasks are then under .../tasks/<task_id>/)
    varlib_latest_pattern = re.compile('^/var/lib/mesos/.*/executors/{}/runs/latest$'.format(path_id))
    for browse_path in agent_executor_paths.keys():
        if varlib_latest_pattern.match(browse_path):
            return browse_path
    # - 1.9: '/var/lib/mesos/.../executors/<executor_id>/runs/<some_uuid>'
    # Marathon: /var/lib/mesos/slave/slaves/b9bbd073-4f4f-4a4d-bdee-68021b7a4c1e-S2/frameworks/b9bbd073-4f4f-4a4d-bdee-68021b7a4c1e-0000/executors/hello-world.bb47e080-02c6-11e8-88f6-760584c8e399/runs/f8de4bc4-620b-4687-a032-3e34c378708f
    # Custom Executor: /var/lib/mesos/slave/slaves/b9bbd073-4f4f-4a4d-bdee-68021b7a4c1e-S2/frameworks/b9bbd073-4f4f-4a4d-bdee-68021b7a4c1e-0002/executors/hello__22a1ee97-23cf-407f-a1d1-7d6a0e325774/runs/5b6831b0-a9b1-482e-8595-8f800c32bdf6
    #                  (tasks share stdout/stderr with the executor)
    varlib_uuid_pattern = re.compile('^/var/lib/mesos/.*/executors/{}/runs/[a-f0-9-]+$'.format(path_id))
    for browse_path in agent_executor_paths.keys():
        if varlib_uuid_pattern.match(browse_path):
            return browse_path

    return ''


def _select_log_files(item: pytest.Item, task_id: str, file_infos: list, source: str, selected: collections.OrderedDict):
    '''Finds and produces the 'stderr'/'stdout' file entries from the provided directory list returned by the agent.

    Results are placed in the 'selected' param.
    '''
    logfile_pattern = re.compile('^.*/(stdout|stderr)(\.[0-9]+)?$')
    for file_info in file_infos:
        if not logfile_pattern.match(file_info['path']):
            continue
        # Example output filename (sort by time):
        # 180125_225944.world-1-server__4d534510-35d9-4f06-811e-e9a9ffa4d14f.task.stdout
        # 180126_000225.hello-0-server__15174696-2d3d-48e9-b492-d9a0cc289786.executor.stderr
        # 180126_002024.hello-world.662e7976-0224-11e8-b2f2-deead5f2b92b.stdout.1
        out_filename = '{}.{}.{}{}'.format(
            time.strftime('%y%m%d_%H%M%S', time.gmtime(file_info['mtime'])),
            task_id,
            source,
            os.path.basename(file_info['path']))
        selected[_setup_artifact_path(item, out_filename)] = file_info


def _setup_artifact_path(item: pytest.Item, artifact_name: str):
    '''Given the pytest item and an artifact_name,
    Returns the path to write an artifact with that name.'''

    # full item.listchain() is e.g.:
    # - ['build', 'frameworks/template/tests/test_sanity.py', 'test_install']
    # - ['build', 'tests/test_sanity.py', 'test_install']
    # we want to turn both cases into: 'logs/test_sanity_py/test_install'
    if _testlogs_test_index > 0:
        # test_index is defined: get name like "05__test_placement_rules"
        test_name = '{:02d}__{}'.format(_testlogs_test_index, item.name)
    else:
        # test_index is not defined: fall back to just "test_placement_rules"
        test_name = item.name

    output_dir = os.path.join(_test_suite_artifact_directory(item), test_name)
    if not os.path.isdir(output_dir):
        os.makedirs(output_dir)

    return os.path.join(output_dir, artifact_name)


def _test_suite_artifact_directory(item: pytest.Item):
    '''Returns the parent directory for the artifacts across a suite of tests.'''
    return os.path.join('logs', get_test_suite_name(item))
