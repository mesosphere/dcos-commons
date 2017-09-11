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
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_hello_node():
    config.check_running()
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-0')
    sdk_tasks.kill_task_with_pattern('hello', 'hello-0-server.hello-world.mesos')
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello-0', hello_ids)

    config.check_running()


@pytest.mark.sanity
@pytest.mark.recovery
def test_pod_restart():
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-0')

    # get current agent id:
    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-0', json=True)
    old_agent = jsonobj[0]['info']['slaveId']['value']

    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod restart hello-0', json=True)
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'hello-0-server'

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello-0', hello_ids)
    config.check_running()

    # check agent didn't move:
    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-0', json=True)
    new_agent = jsonobj[0]['info']['slaveId']['value']
    assert old_agent == new_agent


@pytest.mark.sanity
@pytest.mark.recovery
@sdk_utils.dcos_1_9_or_higher
def test_pods_restart_graceful_shutdown():
    options = {
        "world": {
            "kill_grace_period": 30
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, config.DEFAULT_TASK_COUNT,
                        additional_options=options)

    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'world-0')

    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod restart world-0', json=True)
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'world-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'world-0-server'

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'world-0', world_ids)
    config.check_running()

    # ensure the SIGTERM was sent via the "all clean" message in the world
    # service's signal trap/handler, BUT not the shell command, indicated
    # by "echo".
    stdout = sdk_cmd.run_cli(
        "task log --completed --lines=1000 {}".format(world_ids[0]))
    clean_msg = None
    for s in stdout.split('\n'):
        if s.find('echo') < 0 and s.find('all clean') >= 0:
            clean_msg = s

    assert clean_msg is not None


@pytest.mark.sanity
@pytest.mark.recovery
def test_pod_replace():
    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'world-0')

    # get current agent id (TODO: uncomment if/when agent is guaranteed to change in a replace operation):
    #jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info world-0', json=True)
    #old_agent = jsonobj[0]['info']['slaveId']['value']

    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace world-0', json=True)
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'world-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'world-0-server'

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'world-0', world_ids)
    config.check_running()

    # check agent moved (TODO: uncomment if/when agent is guaranteed to change (may randomly move back to old agent))
    #jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info world-0', json=True)
    #new_agent = jsonobj[0]['info']['slaveId']['value']
    # assert old_agent != new_agent


@pytest.mark.recovery
def test_scheduler_died():
    sdk_tasks.kill_task_with_pattern('helloworld.scheduler.Main', sdk_marathon.get_scheduler_host(config.SERVICE_NAME))
    config.check_running()


@pytest.mark.recovery
def test_all_executors_killed():
    for host in shakedown.get_service_ips(config.SERVICE_NAME):
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
    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'world')
    config.bump_world_cpus()
    sdk_tasks.kill_task_with_pattern('world', 'world-0-server.{}.mesos'.format(config.SERVICE_NAME))
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_kill_all_task_in_node():
    #  kill both world tasks
    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'world')
    hosts = shakedown.get_service_ips(config.SERVICE_NAME)
    config.bump_world_cpus()
    [sdk_tasks.kill_task_with_pattern('world', h) for h in hosts]
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_scheduler_died():
    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'world')
    host = sdk_marathon.get_scheduler_host(config.SERVICE_NAME)
    config.bump_world_cpus()
    sdk_tasks.kill_task_with_pattern('helloworld.scheduler.Main', host)
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_executor_killed():
    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'world')
    config.bump_world_cpus()
    sdk_tasks.kill_task_with_pattern('helloworld.executor.Main', 'world-0-server.{}.mesos'.format(config.SERVICE_NAME))
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_updates_then_all_executors_killed():
    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'world')
    hosts = shakedown.get_service_ips(config.SERVICE_NAME)
    config.bump_world_cpus()
    [sdk_tasks.kill_task_with_pattern('helloworld.executor.Main', h) for h in hosts]
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_master_killed():
    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'world')
    config.bump_world_cpus()
    sdk_tasks.kill_task_with_pattern('mesos-master')
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'world', world_ids)
    config.check_running()


@pytest.mark.recovery
def test_config_update_then_zk_killed():
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello')
    config.bump_hello_cpus()
    sdk_tasks.kill_task_with_pattern('zookeeper')
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello', hello_ids)
    config.check_running()
