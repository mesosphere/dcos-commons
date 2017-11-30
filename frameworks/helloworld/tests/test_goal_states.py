import logging
import re

import dcos.marathon
import pytest
import sdk_cmd
import sdk_install
import sdk_marathon
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
            additional_options={"service": {"name": foldered_name, "spec_file": "examples/finish.yml"}})

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.mark.sanity
def test_install():
    config.check_running(sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.sanity
@pytest.mark.smoke
def test_once_task_does_not_restart_on_config_update():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)

    sdk_plan.wait_for_completed_deployment(service_name)
    task_name = 'hello-0-once'
    hello_once_id = sdk_tasks.get_completed_task_id(task_name)
    assert hello_once_id is not None
    log.info('hello_once_id: ' + str(hello_once_id))

    updated_cpus = config.bump_hello_cpus(foldered_name)

    sdk_tasks.check_task_not_relaunched(foldered_name, task_name, hello_once_id)
    config.check_running(foldered_name)


@pytest.mark.sanity
@pytest.mark.smoke
def test_finish_task_restarts_on_config_update():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)
    task_name = 'world-0-finish'
    world_finish_id = sdk_tasks.get_completed_task_id(task_name)
    assert world_finish_id is not None
    log.info('world_finish_id: ' + str(world_finish_id))

    updated_cpus = config.bump_world_cpus(foldered_name)

    sdk_tasks.check_task_relaunched(task_name, world_finish_id)
    config.check_running(foldered_name)


@pytest.mark.sanity
@pytest.mark.smoke
def test_bump_hello_nodes():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_running(foldered_name)

    hello_ids = sdk_tasks.get_task_ids(foldered_name, 'hello')
    log.info('hello ids: ' + str(hello_ids))

    sdk_marathon.bump_task_count_config(foldered_name, 'HELLO_COUNT')

    config.check_running(foldered_name)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'hello', hello_ids)


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
        sdk_utils.get_foldered_name(config.SERVICE_NAME), 'pod info hello-1', json=True)
    assert len(jsonobj) == 1
    task = jsonobj[0]
    assert len(task) == 2
    assert task['info']['name'] == 'hello-1-server'
    assert task['info']['taskId']['value'] == task['status']['taskId']['value']
    assert task['status']['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_state_properties_get():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)

    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'state properties', json=True)
    # should be in alphabetical order:
    expected = [
        "hello-0-server:task-status",
        "hello-1-server:task-status",
        "last-completed-update-type",
        "world-0-server:task-status",
        "world-1-server:task-status"]
    # the properties list may also have a 'suppressed' bit, which would have been left behind by the
    # prior version when upgrades were being tested during suite setup
    expected_with_suppressed = list(expected)
    expected_with_suppressed.insert(3, 'suppressed')
    assert jsonobj == expected or jsonobj == expected_with_suppressed


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
