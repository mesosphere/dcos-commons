import logging
import re

import dcos.marathon
import pytest
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils
import shakedown
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


@pytest.mark.smoke
def test_install():
    config.check_running(sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.mesos_v0
def test_mesos_v0_api():
    service_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    prior_api_version = sdk_marathon.get_mesos_api_version(service_name)
    if prior_api_version is not "V0":
        sdk_marathon.set_mesos_api_version(service_name, "V0")
        sdk_marathon.set_mesos_api_version(service_name, prior_api_version)


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

    all_tasks = shakedown.get_service_tasks(foldered_name)
    running_tasks = [t for t in all_tasks if t['name'].startswith('hello') and t['state'] == "TASK_RUNNING"]
    assert len(running_tasks) == config.hello_task_count(foldered_name)
    for t in running_tasks:
        assert config.close_enough(t['resources']['cpus'], updated_cpus)


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

    all_tasks = shakedown.get_service_tasks(foldered_name)
    running_tasks = [t for t in all_tasks if t['name'].startswith('world') and t['state'] == "TASK_RUNNING"]
    assert len(running_tasks) == config.world_task_count(foldered_name)
    for t in running_tasks:
        assert config.close_enough(t['resources']['cpus'], updated_cpus)


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
    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod status --json', json=True)
    assert jsonobj['service'] == foldered_name
    for pod in jsonobj['pods']:
        assert re.match('(hello|world)', pod['name'])
        for instance in pod['instances']:
            assert re.match('(hello|world)-[0-9]+', instance['name'])
            for task in instance['tasks']:
                assert len(task) == 3
                assert re.match('(hello|world)-[0-9]+-server__[0-9a-f-]+', task['id'])
                assert re.match('(hello|world)-[0-9]+-server', task['name'])
                assert task['status'] == 'RUNNING'


@pytest.mark.sanity
def test_pod_status_one():
    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME,
        sdk_utils.get_foldered_name(config.SERVICE_NAME), 'pod status --json hello-0', json=True)
    assert jsonobj['name'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    task = jsonobj['tasks'][0]
    assert len(task) == 3
    assert re.match('hello-0-server__[0-9a-f-]+', task['id'])
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

    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'state properties', json=True)
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
        "world-1-server:task-status"]:
        assert required in jsonobj
    # also check that the returned list was in alphabetical order:
    list_sorted = list(jsonobj) # copy
    list_sorted.sort()
    assert list_sorted == jsonobj


@pytest.mark.sanity
def test_state_refresh_disable_cache():
    '''Disables caching via a scheduler envvar'''
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)
    task_ids = sdk_tasks.get_task_ids(foldered_name, '')

    # caching enabled by default:
    stdout = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'state refresh_cache')
    assert "Received cmd: refresh" in stdout

    marathon_config = sdk_marathon.get_config(foldered_name)
    marathon_config['env']['DISABLE_STATE_CACHE'] = 'any-text-here'
    sdk_marathon.update_app(foldered_name, marathon_config)

    sdk_tasks.check_tasks_not_updated(foldered_name, '', task_ids)
    config.check_running(foldered_name)

    # caching disabled, refresh_cache should fail with a 409 error (eventually, once scheduler is up):
    def check_cache_refresh_fails_409conflict():
        output = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'state refresh_cache')
        if "failed: 409 Conflict" in output:
            return True
        return False

    shakedown.wait_for(lambda: check_cache_refresh_fails_409conflict(), timeout_seconds=120.)

    marathon_config = sdk_marathon.get_config(foldered_name)
    del marathon_config['env']['DISABLE_STATE_CACHE']
    sdk_marathon.update_app(foldered_name, marathon_config)

    sdk_tasks.check_tasks_not_updated(foldered_name, '', task_ids)
    config.check_running(foldered_name)
    shakedown.deployment_wait()  # ensure marathon thinks the deployment is complete too

    # caching reenabled, refresh_cache should succeed (eventually, once scheduler is up):
    def check_cache_refresh():
        return sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'state refresh_cache')

    stdout = shakedown.wait_for(lambda: check_cache_refresh(), timeout_seconds=120.)
    assert "Received cmd: refresh" in stdout


@pytest.mark.sanity
def test_lock():
    '''This test verifies that a second scheduler fails to startup when
    an existing scheduler is running.  Without locking, the scheduler
    would fail during registration, but after writing its config to ZK.
    So in order to verify that the scheduler fails immediately, we ensure
    that the ZK config state is unmodified.'''

    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    marathon_client = dcos.marathon.create_client()

    # Get ZK state from running framework
    zk_path = "dcos-service-{}/ConfigTarget".format(foldered_name)
    zk_config_old = shakedown.get_zk_node_data(zk_path)

    # Get marathon app
    app = marathon_client.get_app(foldered_name)
    old_timestamp = app.get("lastTaskFailure", {}).get("timestamp", None)

    # Scale to 2 instances
    labels = app["labels"]
    original_labels = labels.copy()
    labels.pop("MARATHON_SINGLE_INSTANCE_APP")
    marathon_client.update_app(foldered_name, {"labels": labels})
    shakedown.deployment_wait()
    marathon_client.update_app(foldered_name, {"instances": 2})

    # Wait for second scheduler to fail
    def fn():
        timestamp = marathon_client.get_app(foldered_name).get("lastTaskFailure", {}).get("timestamp", None)
        return timestamp != old_timestamp

    shakedown.wait_for(lambda: fn())

    # Verify ZK is unchanged
    zk_config_new = shakedown.get_zk_node_data(zk_path)
    assert zk_config_old == zk_config_new

    # In order to prevent the second scheduler instance from obtaining a lock, we undo the "scale-up" operation
    marathon_client.update_app(foldered_name, {"labels": original_labels, "instances": 1}, force=True)
    shakedown.deployment_wait()
