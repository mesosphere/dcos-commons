import json

import pytest
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_tasks
import sdk_utils
import shakedown
from tests import config


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME)
        sdk_install.install(config.PACKAGE_NAME, config.DEFAULT_TASK_COUNT)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_hello_node():
    config.check_running()
    hello_ids = sdk_tasks.get_task_ids(config.PACKAGE_NAME, 'hello-0')
    sdk_tasks.kill_task_with_pattern('hello', 'hello-0-server.hello-world.mesos')
    sdk_tasks.check_tasks_updated(config.PACKAGE_NAME, 'hello', hello_ids)

    config.check_running()


@pytest.mark.sanity
@pytest.mark.recovery
def test_pod_restart():
    hello_ids = sdk_tasks.get_task_ids(config.PACKAGE_NAME, 'hello-0')

    # get current agent id:
    stdout = sdk_cmd.run_cli('hello-world pod info hello-0')
    old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    stdout = sdk_cmd.run_cli('hello-world pod restart hello-0')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'hello-0-server'

    sdk_tasks.check_tasks_updated(config.PACKAGE_NAME, 'hello', hello_ids)
    config.check_running()

    # check agent didn't move:
    stdout = sdk_cmd.run_cli('hello-world pod info hello-0')
    new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    assert old_agent == new_agent


@pytest.mark.sanity
@pytest.mark.recovery
def test_pod_replace():
    world_ids = sdk_tasks.get_task_ids(config.PACKAGE_NAME, 'world-0')

    # get current agent id (TODO: uncomment if/when agent is guaranteed to change in a replace operation):
    #stdout = sdk_cmd.run_cli('hello-world pod info world-0')
    #old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    jsonobj = json.loads(sdk_cmd.run_cli('hello-world pod replace world-0'))
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'world-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'world-0-server'

    sdk_tasks.check_tasks_updated(config.PACKAGE_NAME, 'world-0', world_ids)
    config.check_running()

    # check agent moved (TODO: uncomment if/when agent is guaranteed to change (may randomly move back to old agent))
    #stdout = sdk_cmd.run_cli('hello-world pod info world-0')
    #new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    # assert old_agent != new_agent


@pytest.mark.recovery
def test_scheduler_died():
    sdk_tasks.kill_task_with_pattern('helloworld.scheduler.Main', sdk_marathon.get_scheduler_host(config.PACKAGE_NAME))
    config.check_running()


@pytest.mark.recovery
def test_all_executors_killed():
    for host in shakedown.get_service_ips(config.PACKAGE_NAME):
        sdk_tasks.kill_task_with_pattern('helloworld.executor.Main', host)
    config.check_running()


@pytest.mark.recovery
def test_master_killed():
    sdk_tasks.kill_task_with_pattern('mesos-master')
    config.check_running()


@pytest.mark.recovery
def test_zk_killed():
    sdk_tasks.kill_task_with_pattern('zookeeper')
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_kill_task_in_node():
    # kill 1 of 2 world tasks
    world_ids = sdk_tasks.get_task_ids(config.PACKAGE_NAME, 'world')
    config.bump_world_cpus()
    sdk_tasks.kill_task_with_pattern('world', 'world-0-server.{}.mesos'.format(config.PACKAGE_NAME))
    sdk_tasks.check_tasks_updated(config.PACKAGE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_kill_all_task_in_node():
    #  kill both world tasks
    world_ids = sdk_tasks.get_task_ids(config.PACKAGE_NAME, 'world')
    hosts = shakedown.get_service_ips(config.PACKAGE_NAME)
    config.bump_world_cpus()
    [sdk_tasks.kill_task_with_pattern('world', h) for h in hosts]
    sdk_tasks.check_tasks_updated(config.PACKAGE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_scheduler_died():
    world_ids = sdk_tasks.get_task_ids(config.PACKAGE_NAME, 'world')
    host = sdk_marathon.get_scheduler_host(config.PACKAGE_NAME)
    config.bump_world_cpus()
    sdk_tasks.kill_task_with_pattern('helloworld.scheduler.Main', host)
    sdk_tasks.check_tasks_updated(config.PACKAGE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_executor_killed():
    world_ids = sdk_tasks.get_task_ids(config.PACKAGE_NAME, 'world')
    config.bump_world_cpus()
    sdk_tasks.kill_task_with_pattern('helloworld.executor.Main', 'world-0-server.{}.mesos'.format(config.PACKAGE_NAME))
    sdk_tasks.check_tasks_updated(config.PACKAGE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_updates_then_all_executors_killed():
    world_ids = sdk_tasks.get_task_ids(config.PACKAGE_NAME, 'world')
    hosts = shakedown.get_service_ips(config.PACKAGE_NAME)
    config.bump_world_cpus()
    [sdk_tasks.kill_task_with_pattern('helloworld.executor.Main', h) for h in hosts]
    sdk_tasks.check_tasks_updated(config.PACKAGE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_master_killed():
    world_ids = sdk_tasks.get_task_ids(config.PACKAGE_NAME, 'world')
    config.bump_world_cpus()
    sdk_tasks.kill_task_with_pattern('mesos-master')
    sdk_tasks.check_tasks_updated(config.PACKAGE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_zk_killed():
    hello_ids = sdk_tasks.get_task_ids(config.PACKAGE_NAME, 'hello')
    config.bump_hello_cpus()
    sdk_tasks.kill_task_with_pattern('zookeeper')
    sdk_tasks.check_tasks_updated(config.PACKAGE_NAME, 'hello', hello_ids)
    config.check_running()
