import logging
import xml.etree.ElementTree as etree

import pytest
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_metrics
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils
import shakedown
from tests import config

log = logging.getLogger(__name__)

FOLDERED_SERVICE_NAME = sdk_utils.get_foldered_name(config.SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME)

        if shakedown.dcos_version_less_than("1.9"):
            # HDFS upgrade in 1.8 is not supported.
            sdk_install.install(
                config.PACKAGE_NAME,
                FOLDERED_SERVICE_NAME,
                config.DEFAULT_TASK_COUNT,
                additional_options={"service": {"name": FOLDERED_SERVICE_NAME}},
                timeout_seconds=30 * 60)
        else:
            sdk_upgrade.test_upgrade(
                config.PACKAGE_NAME,
                FOLDERED_SERVICE_NAME,
                config.DEFAULT_TASK_COUNT,
                additional_options={"service": {"name": FOLDERED_SERVICE_NAME}},
                timeout_seconds=30 * 60)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME)


@pytest.fixture(autouse=True)
def pre_test_setup():
    pass
    #config.check_healthy(service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
def test_endpoints():
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    core_site = etree.fromstring(sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'endpoints core-site.xml'))
    check_properties(core_site, {
        'ha.zookeeper.parent-znode': '/{}/hadoop-ha'.format(sdk_utils.get_zk_path(FOLDERED_SERVICE_NAME))
    })

    hdfs_site = etree.fromstring(sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'endpoints hdfs-site.xml'))
    expect = {
        'dfs.namenode.shared.edits.dir': 'qjournal://{}/hdfs'.format(';'.join([
            sdk_hosts.autoip_host(FOLDERED_SERVICE_NAME, 'journal-{}-node'.format(i), 8485) for i in range(3)])),
    }
    for i in range(2):
        name_node = 'name-{}-node'.format(i)
        expect['dfs.namenode.rpc-address.hdfs.{}'.format(name_node)] = sdk_hosts.autoip_host(FOLDERED_SERVICE_NAME,
                                                                                             name_node, 9001)
        expect['dfs.namenode.http-address.hdfs.{}'.format(name_node)] = sdk_hosts.autoip_host(FOLDERED_SERVICE_NAME,
                                                                                              name_node, 9002)
    check_properties(hdfs_site, expect)


def check_properties(xml, expect):
    found = {}
    for prop in xml.findall('property'):
        name = prop.find('name').text
        if name in expect:
            found[name] = prop.find('value').text
    log.info('expect: {}\nfound:  {}'.format(expect, found))
    assert expect == found


@pytest.mark.recovery
def test_kill_journal_node():
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal-0')
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    sdk_tasks.kill_task_with_pattern('journalnode', sdk_hosts.system_host(FOLDERED_SERVICE_NAME, 'journal-0-node'))
    config.expect_recovery(service_name=FOLDERED_SERVICE_NAME)
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
    config.expect_recovery(service_name=FOLDERED_SERVICE_NAME)
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
    config.expect_recovery(service_name=FOLDERED_SERVICE_NAME)
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_scheduler():
    sdk_tasks.kill_task_with_pattern('hdfs.scheduler.Main', shakedown.get_service_ips('marathon').pop())
    config.check_healthy(service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_journalnodes():
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    for journal_pod in config.get_pod_type_instances("journal", FOLDERED_SERVICE_NAME):
        sdk_cmd.svc_cli(
            config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
            'pod restart {}'.format(journal_pod))

    config.expect_recovery(service_name=FOLDERED_SERVICE_NAME)

    # name nodes fail and restart, so don't check those
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_namenodes():
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    for name_pod in config.get_pod_type_instances("name", FOLDERED_SERVICE_NAME):
        sdk_cmd.svc_cli(
            config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
            'pod restart {}'.format(name_pod))

    config.expect_recovery(service_name=FOLDERED_SERVICE_NAME)

    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_datanodes():
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    for data_pod in config.get_pod_type_instances("data", FOLDERED_SERVICE_NAME):
        sdk_cmd.svc_cli(
            config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
            'pod restart {}'.format(data_pod))

    config.expect_recovery(service_name=FOLDERED_SERVICE_NAME)

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
    config.check_healthy(service_name=FOLDERED_SERVICE_NAME)
    name_0_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-0')
    name_1_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-1')
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'pod replace name-0')
    sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'pod restart name-1')

    config.expect_recovery(service_name=FOLDERED_SERVICE_NAME)

    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-0', name_0_ids)
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-1', name_1_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanent_and_transient_namenode_failures_1_0():
    config.check_healthy(service_name=FOLDERED_SERVICE_NAME)
    name_0_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-0')
    name_1_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-1')
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'pod replace name-1')
    sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'pod restart name-0')

    config.expect_recovery(service_name=FOLDERED_SERVICE_NAME)

    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-0', name_0_ids)
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-1', name_1_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.smoke
def test_install():
    config.check_healthy(service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
def test_bump_journal_cpus():
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')
    log.info('journal ids: ' + str(journal_ids))

    sdk_marathon.bump_cpu_count_config(FOLDERED_SERVICE_NAME, 'JOURNAL_CPUS')

    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    # journal node update should not cause any of the name nodes to crash
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    config.check_healthy(service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
def test_bump_data_nodes():
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')
    log.info('data ids: ' + str(data_ids))

    sdk_marathon.bump_task_count_config(FOLDERED_SERVICE_NAME, 'DATA_COUNT')

    config.check_healthy(
        service_name=FOLDERED_SERVICE_NAME,
        count=config.DEFAULT_TASK_COUNT + 1
    )
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.readiness_check
@pytest.mark.sanity
def test_modify_app_config():
    """This tests checks that the modification of the app config does not trigger a recovery."""
    sdk_plan.wait_for_completed_recovery(FOLDERED_SERVICE_NAME)
    old_recovery_plan = sdk_plan.get_plan(FOLDERED_SERVICE_NAME, "recovery")

    app_config_field = 'TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS'
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')

    marathon_config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    log.info('marathon config: ')
    log.info(marathon_config)
    expiry_ms = int(marathon_config['env'][app_config_field])
    marathon_config['env'][app_config_field] = str(expiry_ms + 1)
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, marathon_config, timeout=15 * 60)

    # All tasks should be updated because hdfs-site.xml has changed
    config.check_healthy(service_name=FOLDERED_SERVICE_NAME)
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'data', journal_ids)

    sdk_plan.wait_for_completed_recovery(FOLDERED_SERVICE_NAME)
    new_recovery_plan = sdk_plan.get_plan(FOLDERED_SERVICE_NAME, "recovery")
    assert old_recovery_plan == new_recovery_plan


@pytest.mark.sanity
def test_modify_app_config_rollback():
    app_config_field = 'TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS'

    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    old_config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    marathon_config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    log.info('marathon config: ')
    log.info(marathon_config)
    expiry_ms = int(marathon_config['env'][app_config_field])
    log.info('expiry ms: ' + str(expiry_ms))
    marathon_config['env'][app_config_field] = str(expiry_ms + 1)
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, marathon_config, timeout=15 * 60)

    # Wait for journal nodes to be affected by the change
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')

    log.info('old config: ')
    log.info(old_config)
    # Put the old config back (rollback)
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, old_config)

    # Wait for the journal nodes to return to their old configuration
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    config.check_healthy(service_name=FOLDERED_SERVICE_NAME)

    marathon_config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    assert int(marathon_config['env'][app_config_field]) == expiry_ms

    # Data tasks should not have been affected
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.metrics
@pytest.mark.local
@sdk_utils.dcos_1_9_or_higher
def test_metrics():
    sdk_metrics.wait_for_any_metrics(
        config.PACKAGE_NAME,
        FOLDERED_SERVICE_NAME,
        "journal-0-node",
        config.DEFAULT_HDFS_TIMEOUT)


def replace_name_node(index):
    config.check_healthy(service_name=FOLDERED_SERVICE_NAME)
    name_node_name = 'name-' + str(index)
    name_id = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, name_node_name)
    journal_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'pod replace {}'.format(name_node_name))

    config.expect_recovery(service_name=FOLDERED_SERVICE_NAME)

    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, name_node_name, name_id)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)
