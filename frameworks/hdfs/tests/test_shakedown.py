import pytest
import xml.etree.ElementTree as etree

import shakedown

import sdk_cmd as cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_metrics
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils
from tests.config import *


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        sdk_install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)
        sdk_utils.gc_frameworks()

        sdk_upgrade.test_upgrade(
            "beta-{}".format(PACKAGE_NAME),
            PACKAGE_NAME,
            DEFAULT_TASK_COUNT,
            service_name=FOLDERED_SERVICE_NAME,
            additional_options={"service": {"name": FOLDERED_SERVICE_NAME}})

        yield # let the test session execute
    finally:
        sdk_install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)


@pytest.fixture(autouse=True)
def pre_test_setup():
    check_healthy()


@pytest.mark.sanity
def test_endpoints():
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    core_site = etree.fromstring(cmd.run_cli('hdfs --name={} endpoints core-site.xml'.format(FOLDERED_SERVICE_NAME)))
    check_properties(core_site, {
        'ha.zookeeper.parent-znode': '/dcos-service-{}/hadoop-ha'.format(ZK_SERVICE_PATH)
    })

    hdfs_site = etree.fromstring(cmd.run_cli('hdfs --name={} endpoints hdfs-site.xml'.format(FOLDERED_SERVICE_NAME)))
    expect = {
        'dfs.namenode.shared.edits.dir': 'qjournal://' + ';'.join([
            sdk_hosts.autoip_host(FOLDERED_SERVICE_NAME, 'journal-{}-node'.format(i), 8485) for i in range(3)]) + '/hdfs',
    }
    for i in range(2):
        expect['dfs.namenode.rpc-address.hdfs.name-{}-node'.format(i)] = sdk_hosts.autoip_host(FOLDERED_SERVICE_NAME, 'name-{}-node'.format(i), 9001)
        expect['dfs.namenode.http-address.hdfs.name-{}-node'.format(i)] = sdk_hosts.autoip_host(FOLDERED_SERVICE_NAME, 'name-{}-node'.format(i), 9002)
    check_properties(hdfs_site, expect)


def check_properties(xml, expect):
    found = {}
    for prop in xml.findall('property'):
        name = prop.find('name').text
        if name in expect:
            found[name] = prop.find('value').text
    sdk_utils.out('expect: {}\nfound:  {}'.format(expect, found))
    assert expect == found


@pytest.mark.recovery
def test_kill_journal_node():
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal-0')
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    sdk_tasks.kill_task_with_pattern('journalnode', sdk_hosts.system_host(FOLDERED_SERVICE_NAME, 'journal-0-node'))
    check_healthy()
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_name_node():
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-0')
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    sdk_tasks.kill_task_with_pattern('namenode', sdk_hosts.system_host(FOLDERED_SERVICE_NAME, 'name-0-node'))
    check_healthy()
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_data_node():
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data-0')
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')

    sdk_tasks.kill_task_with_pattern('datanode', sdk_hosts.system_host(FOLDERED_SERVICE_NAME, 'data-0-node'))
    check_healthy()
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_scheduler():
    sdk_tasks.kill_task_with_pattern('hdfs.scheduler.Main', shakedown.get_service_ips('marathon').pop())
    check_healthy()


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_journalnodes():
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    for host in shakedown.get_service_ips(FOLDERED_SERVICE_NAME):
        sdk_tasks.kill_task_with_pattern('journalnode', host)

    check_healthy()
    # name nodes fail and restart, so don't check those
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_namenodes():
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    for host in shakedown.get_service_ips(FOLDERED_SERVICE_NAME):
        sdk_tasks.kill_task_with_pattern('namenode', host)

    check_healthy()
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_datanodes():
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    for host in shakedown.get_service_ips(FOLDERED_SERVICE_NAME):
        sdk_tasks.kill_task_with_pattern('datanode', host)

    check_healthy()
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanently_replace_namenodes():
    replace_name_node(0)
    replace_name_node(1)
    replace_name_node(0)


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanent_and_transient_namenode_failures_0_1():
    check_healthy()
    name_0_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-0')
    name_1_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-1')
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    cmd.run_cli('hdfs --name={} pod replace name-0'.format(FOLDERED_SERVICE_NAME))
    cmd.run_cli('hdfs --name={} pod restart name-1'.format(FOLDERED_SERVICE_NAME))

    check_healthy()
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-0', name_0_ids)
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-1', name_1_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)

@pytest.mark.sanity
@pytest.mark.recovery
def test_permanent_and_transient_namenode_failures_1_0():
    check_healthy()
    name_0_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-0')
    name_1_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-1')
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    cmd.run_cli('hdfs --name={} pod replace name-1'.format(FOLDERED_SERVICE_NAME))
    cmd.run_cli('hdfs --name={} pod restart name-0'.format(FOLDERED_SERVICE_NAME))

    check_healthy()
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-0', name_0_ids)
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-1', name_1_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.smoke
def test_install():
    check_healthy()


@pytest.mark.sanity
def test_bump_journal_cpus():
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    sdk_utils.out('journal ids: ' + str(journal_ids))

    sdk_marathon.bump_cpu_count_config(FOLDERED_SERVICE_NAME, 'JOURNAL_CPUS')

    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    check_healthy()


@pytest.mark.sanity
def test_bump_data_nodes():
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')
    sdk_utils.out('data ids: ' + str(data_ids))

    sdk_marathon.bump_task_count_config(FOLDERED_SERVICE_NAME, 'DATA_COUNT')

    check_healthy(DEFAULT_TASK_COUNT + 1)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.readiness_check
@pytest.mark.sanity
def test_modify_app_config():
    sdk_plan.wait_for_completed_recovery(FOLDERED_SERVICE_NAME)
    #old_recovery_plan = sdk_plan.get_plan(FOLDERED_SERVICE_NAME, "recovery")

    app_config_field = 'TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS'
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')

    config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    sdk_utils.out('marathon config: ')
    sdk_utils.out(config)
    expiry_ms = int(config['env'][app_config_field])
    config['env'][app_config_field] = str(expiry_ms + 1)
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, config, timeout=15 * 60)

    # All tasks should be updated because hdfs-site.xml has changed
    check_healthy()
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'data', journal_ids)

    sdk_plan.wait_for_completed_recovery(FOLDERED_SERVICE_NAME)
    #new_recovery_plan = sdk_plan.get_plan(FOLDERED_SERVICE_NAME, "recovery")
    #assert(old_recovery_plan == new_recovery_plan)

@pytest.mark.sanity
def test_modify_app_config_rollback():
    app_config_field = 'TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS'

    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    old_config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    sdk_utils.out('marathon config: ')
    sdk_utils.out(config)
    expiry_ms = int(config['env'][app_config_field])
    sdk_utils.out('expiry ms: ' + str(expiry_ms))
    config['env'][app_config_field] = str(expiry_ms + 1)
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, config, timeout= 15 * 60)

    # Wait for journal nodes to be affected by the change
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')

    sdk_utils.out('old config: ')
    sdk_utils.out(old_config)
    # Put the old config back (rollback)
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, old_config)

    # Wait for the journal nodes to return to their old configuration
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    check_healthy()

    config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    assert int(config['env'][app_config_field]) == expiry_ms

    # Data tasks should not have been affected
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.metrics
@sdk_utils.dcos_1_9_or_higher
def test_metrics():
    sdk_metrics.wait_for_any_metrics(FOLDERED_SERVICE_NAME, "journal-0-node", DEFAULT_HDFS_TIMEOUT)


def replace_name_node(index):
    check_healthy()
    name_node_name = 'name-' + str(index)
    name_id = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, name_node_name)
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    cmd.run_cli('hdfs --name={} pod replace {}'.format(FOLDERED_SERVICE_NAME, name_node_name))

    check_healthy()
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, name_node_name, name_id)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


def check_healthy(count=DEFAULT_TASK_COUNT):
    sdk_plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME, timeout_seconds=25 * 60)
    sdk_plan.wait_for_completed_recovery(FOLDERED_SERVICE_NAME, timeout_seconds=25 * 60)
    sdk_tasks.check_running(FOLDERED_SERVICE_NAME, count)
