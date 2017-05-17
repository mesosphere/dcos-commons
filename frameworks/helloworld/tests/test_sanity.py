import json
import re

import dcos.marathon
import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_spin as spin
import sdk_tasks as tasks
import sdk_test_upgrade
import sdk_utils
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    configured_task_count,
    hello_task_count,
    world_task_count,
    check_running,
    bump_hello_cpus,
    bump_world_cpus
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


def close_enough(val0, val1):
    epsilon = 0.00001
    diff = abs(val0 - val1)
    return diff < epsilon


@pytest.mark.smoke
def test_install():
    check_running()


@pytest.mark.sanity
@pytest.mark.smoke
def test_bump_hello_cpus():
    check_running()
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')
    sdk_utils.out('hello ids: ' + str(hello_ids))

    updated_cpus = bump_hello_cpus()

    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', hello_ids)
    check_running()

    all_tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    running_tasks = [t for t in all_tasks if t['name'].startswith('hello') and t['state'] == "TASK_RUNNING"]
    assert len(running_tasks) == hello_task_count()
    for t in running_tasks:
        assert close_enough(t['resources']['cpus'], updated_cpus)


@pytest.mark.sanity
@pytest.mark.smoke
def test_bump_world_cpus():
    check_running()
    world_ids = tasks.get_task_ids(PACKAGE_NAME, 'world')
    sdk_utils.out('world ids: ' + str(world_ids))

    updated_cpus = bump_world_cpus()

    tasks.check_tasks_updated(PACKAGE_NAME, 'world', world_ids)
    check_running()

    all_tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    running_tasks = [t for t in all_tasks if t['name'].startswith('world') and t['state'] == "TASK_RUNNING"]
    assert len(running_tasks) == world_task_count()
    for t in running_tasks:
        assert close_enough(t['resources']['cpus'], updated_cpus)


@pytest.mark.sanity
@pytest.mark.smoke
def test_bump_hello_nodes():
    check_running()

    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')
    sdk_utils.out('hello ids: ' + str(hello_ids))

    marathon.bump_task_count_config(PACKAGE_NAME, 'HELLO_COUNT')

    check_running()
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'hello', hello_ids)


@pytest.mark.sanity
def test_pods_list():
    stdout = cmd.run_cli('hello-world pods list')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == configured_task_count()
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
def test_pods_status_all():
    stdout = cmd.run_cli('hello-world pods status')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == configured_task_count()
    for k, v in jsonobj.items():
        assert re.match('(hello|world)-[0-9]+', k)
        assert len(v) == 1
        task = v[0]
        assert len(task) == 3
        assert re.match('(hello|world)-[0-9]+-server__[0-9a-f-]+', task['id'])
        assert re.match('(hello|world)-[0-9]+-server', task['name'])
        assert task['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_pods_status_one():
    stdout = cmd.run_cli('hello-world pods status hello-0')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 1
    task = jsonobj[0]
    assert len(task) == 3
    assert re.match('hello-0-server__[0-9a-f-]+', task['id'])
    assert task['name'] == 'hello-0-server'
    assert task['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_pods_info():
    stdout = cmd.run_cli('hello-world pods info world-1')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 1
    task = jsonobj[0]
    assert len(task) == 2
    assert task['info']['name'] == 'world-1-server'
    assert task['info']['taskId']['value'] == task['status']['taskId']['value']
    assert task['status']['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_state_properties_get():
    # 'suppressed' could be missing if the scheduler recently started, loop for a bit just in case:
    def check_for_nonempty_properties():
        stdout = cmd.run_cli('hello-world state properties')
        return len(json.loads(stdout)) > 0

    spin.time_wait_noisy(lambda: check_for_nonempty_properties(), timeout_seconds=30)

    stdout = cmd.run_cli('hello-world state properties')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 2
    assert jsonobj[0] == "suppressed"
    assert jsonobj[1] == "last-completed-update-type"

    stdout = cmd.run_cli('hello-world state property suppressed')
    assert stdout == "true\n"


@pytest.mark.speedy
def test_state_refresh_disable_cache():
    '''Disables caching via a scheduler envvar'''
    check_running()
    task_ids = tasks.get_task_ids(PACKAGE_NAME, '')

    # caching enabled by default:
    stdout = cmd.run_cli('hello-world state refresh_cache')
    assert "Received cmd: refresh" in stdout

    config = marathon.get_config(PACKAGE_NAME)
    config['env']['DISABLE_STATE_CACHE'] = 'any-text-here'
    cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    tasks.check_tasks_not_updated(PACKAGE_NAME, '', task_ids)
    check_running()

    # caching disabled, refresh_cache should fail with a 409 error (eventually, once scheduler is up):
    def check_cache_refresh_fails_409conflict():
        try:
            cmd.run_cli('hello-world state refresh_cache')
        except Exception as e:
            if "failed: 409 Conflict" in e.args[0]:
                return True
        return False

    spin.time_wait_noisy(lambda: check_cache_refresh_fails_409conflict(), timeout_seconds=120.)

    config = marathon.get_config(PACKAGE_NAME)
    del config['env']['DISABLE_STATE_CACHE']
    cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    tasks.check_tasks_not_updated(PACKAGE_NAME, '', task_ids)
    check_running()

    # caching reenabled, refresh_cache should succeed (eventually, once scheduler is up):
    def check_cache_refresh():
        return cmd.run_cli('hello-world state refresh_cache')

    stdout = spin.time_wait_return(lambda: check_cache_refresh(), timeout_seconds=120.)
    assert "Received cmd: refresh" in stdout


@pytest.mark.sanity
def test_lock():
    '''This test verifies that a second scheduler fails to startup when
    an existing scheduler is running.  Without locking, the scheduler
    would fail during registration, but after writing its config to ZK.
    So in order to verify that the scheduler fails immediately, we ensure
    that the ZK config state is unmodified.'''

    marathon_client = dcos.marathon.create_client()

    # Get ZK state from running framework
    zk_path = "dcos-service-{}/ConfigTarget".format(PACKAGE_NAME)
    zk_config_old = shakedown.get_zk_node_data(zk_path)

    # Get marathon app
    app_id = "/{}".format(PACKAGE_NAME)
    app = marathon_client.get_app(app_id)
    old_timestamp = app.get("lastTaskFailure", {}).get("timestamp", None)

    # Scale to 2 instances
    labels = app["labels"]
    labels.pop("MARATHON_SINGLE_INSTANCE_APP")
    marathon_client.update_app(app_id, {"labels": labels})
    shakedown.deployment_wait()
    marathon_client.update_app(app_id, {"instances": 2})

    # Wait for second scheduler to fail
    def fn():
        timestamp = marathon_client.get_app(app_id).get("lastTaskFailure", {}).get("timestamp", None)
        return timestamp != old_timestamp

    spin.time_wait_noisy(lambda: fn())

    # Verify ZK is unchanged
    zk_config_new = shakedown.get_zk_node_data(zk_path)
    assert zk_config_old == zk_config_new


@pytest.mark.upgrade
@pytest.mark.sanity
def test_upgrade_downgrade():
    sdk_test_upgrade.upgrade_downgrade(PACKAGE_NAME, PACKAGE_NAME, DEFAULT_TASK_COUNT)
