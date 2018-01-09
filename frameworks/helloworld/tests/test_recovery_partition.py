import pytest
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_tasks
import sdk_utils
import shakedown
from tests import config


# NOTE: It probably seems a bit weird that this is in its own set of recovery tests instead of being in
# the test_recovery suite. WHY?!?! you may be screaming into the void? Because, Shakedown uses a library called
# Paramiko to do SSH and that library does not handle lots of rapid SSHing very well. By separating these
# Into their own suite, we introduce some delay which reduces this issue.

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.recovery
@pytest.mark.skip(reason="DCOS-20123")
def test_partition():
    host = sdk_hosts.system_host(config.SERVICE_NAME, "hello-0-server")
    shakedown.partition_agent(host)
    shakedown.reconnect_agent(host)
    config.check_running()


@pytest.mark.recovery
@pytest.mark.skip(reason="DCOS-20123")
def test_partition_master_both_ways():
    shakedown.partition_master()
    shakedown.reconnect_master()
    config.check_running()


@pytest.mark.recovery
@pytest.mark.skip(reason="DCOS-20123")
def test_partition_master_incoming():
    shakedown.partition_master(incoming=True, outgoing=False)
    shakedown.reconnect_master()
    config.check_running()


@pytest.mark.recovery
@pytest.mark.skip(reason="DCOS-20123")
def test_partition_master_outgoing():
    shakedown.partition_master(incoming=False, outgoing=True)
    shakedown.reconnect_master()
    config.check_running()


@pytest.mark.recovery
@pytest.mark.skip(reason="DCOS-20123")
def test_all_partition():
    hosts = shakedown.get_service_ips(config.SERVICE_NAME)
    for host in hosts:
        shakedown.partition_agent(host)
    for host in hosts:
        shakedown.reconnect_agent(host)
    config.check_running()


@pytest.mark.recovery
@pytest.mark.skip(reason="DCOS-20123")
def test_config_update_while_partitioned():
    world_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'world')
    host = sdk_hosts.system_host(config.SERVICE_NAME, "world-0-server")
    shakedown.partition_agent(host)

    service_config = sdk_marathon.get_config(config.SERVICE_NAME)
    updated_cpus = float(service_config['env']['WORLD_CPUS']) + 0.1
    service_config['env']['WORLD_CPUS'] = str(updated_cpus)
    sdk_marathon.update_app(config.SERVICE_NAME, service_config, wait_for_completed_deployment=False)

    shakedown.reconnect_agent(host)
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'world', world_ids)
    config.check_running()
    all_tasks = shakedown.get_service_tasks(config.SERVICE_NAME)
    running_tasks = [t for t in all_tasks if t['name'].startswith('world') and t['state'] == "TASK_RUNNING"]
    assert len(running_tasks) == config.world_task_count(config.SERVICE_NAME)
    for t in running_tasks:
        assert config.close_enough(t['resources']['cpus'], updated_cpus)
