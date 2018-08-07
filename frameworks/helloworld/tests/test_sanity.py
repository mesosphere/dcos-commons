import logging
import re

import pytest
import retrying
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils
from tests import config


log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
        sdk_upgrade.test_upgrade(
            config.PACKAGE_NAME,
            foldered_name,
            config.DEFAULT_TASK_COUNT,
            additional_options={"service": {"name": foldered_name}})

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.mark.sanity
@pytest.mark.smoke
def test_install():
    config.check_running(sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.sanity
@pytest.mark.smoke
def test_bump_hello_cpus():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)
    hello_ids = sdk_tasks.get_task_ids(foldered_name, 'hello')
    log.info('hello ids: ' + str(hello_ids))

    updated_cpus = config.bump_hello_cpus(foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, 'hello', hello_ids)
    config.check_running(foldered_name)

    all_tasks = sdk_tasks.get_service_tasks(foldered_name, task_prefix='hello')
    running_tasks = [t for t in all_tasks if t.state == "TASK_RUNNING"]
    assert len(running_tasks) == config.hello_task_count(foldered_name)
    for t in running_tasks:
        assert config.close_enough(t.resources['cpus'], updated_cpus)


@pytest.mark.sanity
@pytest.mark.smoke
def test_bump_world_cpus():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)

    original_world_ids = sdk_tasks.get_task_ids(foldered_name, 'world')
    log.info('world ids: ' + str(original_world_ids))

    updated_cpus = config.bump_world_cpus(foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, 'world', original_world_ids)
    config.check_running(foldered_name)

    all_tasks = sdk_tasks.get_service_tasks(foldered_name, task_prefix='world')
    running_tasks = [t for t in all_tasks if t.state == "TASK_RUNNING"]
    assert len(running_tasks) == config.world_task_count(foldered_name)
    for t in running_tasks:
        assert config.close_enough(t.resources['cpus'], updated_cpus)


@pytest.mark.sanity
@pytest.mark.smoke
def test_increase_decrease_world_nodes():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)

    original_hello_ids = sdk_tasks.get_task_ids(foldered_name, 'hello')
    original_world_ids = sdk_tasks.get_task_ids(foldered_name, 'world')
    log.info('world ids: ' + str(original_world_ids))

    # add 2 world nodes
    sdk_marathon.bump_task_count_config(foldered_name, 'WORLD_COUNT', 2)

    config.check_running(foldered_name)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'world', original_world_ids)

    # check 2 world tasks added:
    assert 2 + len(original_world_ids) == len(sdk_tasks.get_task_ids(foldered_name, 'world'))

    # subtract 2 world nodes
    sdk_marathon.bump_task_count_config(foldered_name, 'WORLD_COUNT', -2)

    config.check_running(foldered_name)
    # wait for the decommission plan for this subtraction to be complete
    sdk_plan.wait_for_completed_plan(foldered_name, 'decommission')
    # check that the total task count is back to original
    sdk_tasks.check_running(
        foldered_name,
        len(original_hello_ids) + len(original_world_ids),
        allow_more=False)
    # check that original tasks weren't affected/relaunched in the process
    sdk_tasks.check_tasks_not_updated(foldered_name, 'hello', original_hello_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'world', original_world_ids)

    # check that the world tasks are back to their prior state (also without changing task ids)
    assert original_world_ids == sdk_tasks.get_task_ids(foldered_name, 'world')


@pytest.mark.sanity
def test_pod_list():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod list', json=True)
    assert len(jsonobj) == config.configured_task_count(foldered_name)
    # expect: X instances of 'hello-#' followed by Y instances of 'world-#',
    # in alphanumerical order
    first_world = -1
    for i in range(len(jsonobj)):
        entry = jsonobj[i]
        if first_world < 0:
            if entry.startswith('world-'):
                first_world = i
        if first_world == -1:
            assert jsonobj[i] == 'hello-{}'.format(i)
        else:
            assert jsonobj[i] == 'world-{}'.format(i - first_world)


@pytest.mark.sanity
def test_pod_status_all():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    # /test/integration/hello-world => test.integration.hello-world
    sanitized_name = sdk_utils.get_task_id_service_name(foldered_name)
    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod status --json', json=True)
    assert jsonobj['service'] == foldered_name
    for pod in jsonobj['pods']:
        assert re.match('(hello|world)', pod['name'])
        for instance in pod['instances']:
            assert re.match('(hello|world)-[0-9]+', instance['name'])
            for task in instance['tasks']:
                assert len(task) == 3
                assert re.match(sanitized_name + '__(hello|world)-[0-9]+-server__[0-9a-f-]+', task['id'])
                assert re.match('(hello|world)-[0-9]+-server', task['name'])
                assert task['status'] == 'RUNNING'


@pytest.mark.sanity
def test_pod_status_one():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    # /test/integration/hello-world => test.integration.hello-world
    sanitized_name = sdk_utils.get_task_id_service_name(foldered_name)
    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod status --json hello-0', json=True)
    assert jsonobj['name'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    task = jsonobj['tasks'][0]
    assert len(task) == 3
    assert re.match(sanitized_name + '__hello-0-server__[0-9a-f-]+', task['id'])
    assert task['name'] == 'hello-0-server'
    assert task['status'] == 'RUNNING'


@pytest.mark.sanity
def test_pod_info():
    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME,
                              sdk_utils.get_foldered_name(config.SERVICE_NAME), 'pod info world-1', json=True)
    assert len(jsonobj) == 1
    task = jsonobj[0]
    assert len(task) == 2
    assert task['info']['name'] == 'world-1-server'
    assert task['info']['taskId']['value'] == task['status']['taskId']['value']
    assert task['status']['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_state_properties_get():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)

    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'debug state properties', json=True)
    # Just check that some expected properties are present. The following may also be present:
    # - "suppressed": Depends on internal scheduler state at the time of the query.
    # - "world-[2,3]-server:task-status": Leftovers from an earlier expansion to 4 world tasks.
    #     In theory, the SDK could clean these up as part of the decommission operation, but they
    #     won't hurt or affect the service, and are arguably useful in terms of leaving behind some
    #     evidence of pods that had existed prior to a decommission operation.
    for required in [
        "hello-0-server:task-status",
        "last-completed-update-type",
        "world-0-server:task-status",
        "world-1-server:task-status"
    ]:
        assert required in jsonobj
    # also check that the returned list was in alphabetical order:
    list_sorted = list(jsonobj)  # copy
    list_sorted.sort()
    assert list_sorted == jsonobj


@pytest.mark.sanity
def test_help_cli():
    sdk_cmd.svc_cli(config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME), 'help')


@pytest.mark.sanity
def test_config_cli():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    configs = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'debug config list', json=True)
    assert len(configs) >= 1  # refrain from breaking this test if earlier tests did a config update

    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
                           'debug config show {}'.format(configs[0]), print_output=False)  # noisy output
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'debug config target', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'debug config target_id', json=True)


@pytest.mark.sanity
@pytest.mark.smoke  # include in smoke: verify that cluster is healthy after earlier service config changes
def test_plan_cli():
    plan_name = 'deploy'
    phase_name = 'world'
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'plan list', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'plan show {}'.format(plan_name))
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
                           'plan show --json {}'.format(plan_name), json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
                           'plan show {} --json'.format(plan_name), json=True)

    # trigger a restart so that the plan is in a non-complete state.
    # the 'interrupt' command will fail if the plan is already complete:
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'plan force-restart {}'.format(plan_name))

    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
                           'plan interrupt {} {}'.format(plan_name, phase_name))
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
                           'plan continue {} {}'.format(plan_name, phase_name))

    # now wait for plan to finish before continuing to other tests:
    assert sdk_plan.wait_for_completed_plan(foldered_name, plan_name)


@pytest.mark.sanity
def test_state_cli():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'debug state framework_id', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'debug state properties', json=True)


@pytest.mark.sanity
def test_state_refresh_disable_cache():
    '''Disables caching via a scheduler envvar'''
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)
    task_ids = sdk_tasks.get_task_ids(foldered_name, '')

    # caching enabled by default:
    stdout = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'debug state refresh_cache')
    assert "Received cmd: refresh" in stdout

    marathon_config = sdk_marathon.get_config(foldered_name)
    marathon_config['env']['DISABLE_STATE_CACHE'] = 'any-text-here'
    sdk_marathon.update_app(foldered_name, marathon_config)

    sdk_tasks.check_tasks_not_updated(foldered_name, '', task_ids)
    config.check_running(foldered_name)

    # caching disabled, refresh_cache should fail with a 409 error (eventually, once scheduler is up):
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=120 * 1000,
        retry_on_result=lambda res: not res)
    def check_cache_refresh_fails_409conflict():
        output = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            foldered_name,
            'debug state refresh_cache',
            return_stderr_in_stdout=True)
        return "failed: 409 Conflict" in output

    check_cache_refresh_fails_409conflict()

    marathon_config = sdk_marathon.get_config(foldered_name)
    del marathon_config['env']['DISABLE_STATE_CACHE']
    sdk_marathon.update_app(foldered_name, marathon_config)

    sdk_tasks.check_tasks_not_updated(foldered_name, '', task_ids)
    config.check_running(foldered_name)

    # caching reenabled, refresh_cache should succeed (eventually, once scheduler is up):
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=120 * 1000,
        retry_on_result=lambda res: not res)
    def check_cache_refresh():
        return sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'debug state refresh_cache')

    stdout = check_cache_refresh()
    assert "Received cmd: refresh" in stdout


@pytest.mark.sanity
def test_lock():
    '''This test verifies that a second scheduler fails to startup when
    an existing scheduler is running.  Without locking, the scheduler
    would fail during registration, but after writing its config to ZK.
    So in order to verify that the scheduler fails immediately, we ensure
    that the ZK config state is unmodified.'''

    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)

    def get_zk_node_data(node_name):
        return sdk_cmd.cluster_request("GET", "/exhibitor/exhibitor/v1/explorer/node-data?key={}".format(node_name)).json()

    # Get ZK state from running framework
    zk_path = "{}/ConfigTarget".format(sdk_utils.get_zk_path(foldered_name))
    zk_config_old = get_zk_node_data(zk_path)

    # Get marathon app
    marathon_config = sdk_marathon.get_config(foldered_name)
    old_timestamp = marathon_config.get("lastTaskFailure", {}).get("timestamp", None)

    # Scale to 2 instances
    labels = marathon_config["labels"]
    original_labels = labels.copy()
    labels.pop("MARATHON_SINGLE_INSTANCE_APP")
    sdk_marathon.update_app(foldered_name, marathon_config)
    marathon_config["instances"] = 2
    sdk_marathon.update_app(foldered_name, marathon_config, wait_for_completed_deployment=False)

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=120 * 1000,
        retry_on_result=lambda res: not res)
    def wait_for_second_scheduler_to_fail():
        timestamp = sdk_marathon.get_config(foldered_name).get("lastTaskFailure", {}).get("timestamp", None)
        return timestamp != old_timestamp

    wait_for_second_scheduler_to_fail()

    # Verify ZK is unchanged
    zk_config_new = get_zk_node_data(zk_path)
    assert zk_config_old == zk_config_new

    # In order to prevent the second scheduler instance from obtaining a lock, we undo the "scale-up" operation
    marathon_config["instances"] = 1
    marathon_config["labels"] = original_labels
    sdk_marathon.update_app(foldered_name, marathon_config, force=True)


@pytest.mark.sanity
def test_tmp_directory_created():
    code, stdout, stderr = sdk_cmd.service_task_exec(config.SERVICE_NAME, "hello-0-server", "echo bar > /tmp/bar && cat tmp/bar | grep bar")
    assert code > 0
