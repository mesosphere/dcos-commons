import json

import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_tasks as tasks
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    check_running,
    bump_world_cpus,
    bump_hello_cpus
)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        install.uninstall(PACKAGE_NAME)
        install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)

        yield # let the test session execute
    finally:
        install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_hello_node():
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello-0')
    tasks.kill_task_with_pattern('hello', 'hello-0-server.hello-world.mesos')
    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', hello_ids)

    check_running()


@pytest.mark.sanity
@pytest.mark.recovery
def test_pods_restart():
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello-0')

    # get current agent id:
    stdout = cmd.run_cli('hello-world pods info hello-0')
    old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    stdout = cmd.run_cli('hello-world pods restart hello-0')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'hello-0-server'

    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', hello_ids)
    check_running()

    # check agent didn't move:
    stdout = cmd.run_cli('hello-world pods info hello-0')
    new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    assert old_agent == new_agent


@pytest.mark.sanity
@pytest.mark.recovery
def test_pods_replace():
    world_ids = tasks.get_task_ids(PACKAGE_NAME, 'world-0')

    # get current agent id:
    stdout = cmd.run_cli('hello-world pods info world-0')
    old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    jsonobj = json.loads(cmd.run_cli('hello-world pods replace world-0'))
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'world-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'world-0-server'

    tasks.check_tasks_updated(PACKAGE_NAME, 'world-0', world_ids)
    check_running()

    # check agent moved:
    stdout = cmd.run_cli('hello-world pods info world-0')
    new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    # TODO: enable assert if/when agent is guaranteed to change (may randomly move back to old agent)
    # assert old_agent != new_agent


@pytest.mark.recovery
def test_scheduler_died():
    tasks.kill_task_with_pattern('helloworld.scheduler.Main', marathon.get_scheduler_host(PACKAGE_NAME))
    check_running()


@pytest.mark.recovery
def test_all_executors_killed():
    for host in shakedown.get_service_ips(PACKAGE_NAME):
        tasks.kill_task_with_pattern('helloworld.executor.Main', host)
    check_running()


@pytest.mark.recovery
def test_master_killed():
    tasks.kill_task_with_pattern('mesos-master')
    check_running()


@pytest.mark.recovery
def test_zk_killed():
    tasks.kill_task_with_pattern('zookeeper')
    check_running()


@pytest.mark.recovery
def test_config_update_then_kill_task_in_node():
    # kill 1 of 2 world tasks
    world_ids = tasks.get_task_ids(PACKAGE_NAME, 'world')
    bump_world_cpus()
    tasks.kill_task_with_pattern('world', 'world-0-server.{}.mesos'.format(PACKAGE_NAME))
    tasks.check_tasks_updated(PACKAGE_NAME, 'world', world_ids)
    check_running()


@pytest.mark.recovery
def test_config_update_then_kill_all_task_in_node():
    #  kill both world tasks
    world_ids = tasks.get_task_ids(PACKAGE_NAME, 'world')
    bump_world_cpus()
    hosts = shakedown.get_service_ips(PACKAGE_NAME)
    [tasks.kill_task_with_pattern('world', h) for h in hosts]
    tasks.check_tasks_updated(PACKAGE_NAME, 'world', world_ids)
    check_running()


@pytest.mark.recovery
def test_config_update_then_scheduler_died():
    world_ids = tasks.get_task_ids(PACKAGE_NAME, 'world')
    host = marathon.get_scheduler_host(PACKAGE_NAME)
    bump_world_cpus()
    tasks.kill_task_with_pattern('helloworld.scheduler.Main', host)
    tasks.check_tasks_updated(PACKAGE_NAME, 'world', world_ids)
    check_running()


@pytest.mark.recovery
def test_config_update_then_executor_killed():
    world_ids = tasks.get_task_ids(PACKAGE_NAME, 'world')
    bump_world_cpus()
    tasks.kill_task_with_pattern('helloworld.executor.Main', 'world-0-server.{}.mesos'.format(PACKAGE_NAME))
    tasks.check_tasks_updated(PACKAGE_NAME, 'world', world_ids)
    check_running()


@pytest.mark.recovery
def test_config_updates_then_all_executors_killed():
    world_ids = tasks.get_task_ids(PACKAGE_NAME, 'world')
    bump_world_cpus()
    hosts = shakedown.get_service_ips(PACKAGE_NAME)
    [tasks.kill_task_with_pattern('helloworld.executor.Main', h) for h in hosts]
    tasks.check_tasks_updated(PACKAGE_NAME, 'world', world_ids)
    check_running()


@pytest.mark.recovery
def test_config_update_then_master_killed():
    world_ids = tasks.get_task_ids(PACKAGE_NAME, 'world')
    bump_world_cpus()
    tasks.kill_task_with_pattern('mesos-master')
    tasks.check_tasks_updated(PACKAGE_NAME, 'world', world_ids)
    check_running()


@pytest.mark.recovery
def test_config_update_then_zk_killed():
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')
    bump_hello_cpus()
    tasks.kill_task_with_pattern('zookeeper')
    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', hello_ids)
    check_running()
