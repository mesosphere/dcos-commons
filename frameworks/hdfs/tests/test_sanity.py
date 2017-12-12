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


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        if sdk_utils.dcos_version_less_than("1.9"):
            # HDFS upgrade in 1.8 is not supported.
            sdk_install.install(
                config.PACKAGE_NAME,
                foldered_name,
                config.DEFAULT_TASK_COUNT,
                additional_options={"service": {"name": foldered_name}},
                timeout_seconds=30 * 60)
        else:
            sdk_upgrade.test_upgrade(
                config.PACKAGE_NAME,
                foldered_name,
                config.DEFAULT_TASK_COUNT,
                additional_options={"service": {"name": foldered_name}},
                timeout_seconds=30 * 60)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.fixture(autouse=True)
def pre_test_setup():
    config.check_healthy(service_name=sdk_utils.get_foldered_name(config.SERVICE_NAME))


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
def test_endpoints():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    core_site = etree.fromstring(sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'endpoints core-site.xml'))
    check_properties(core_site, {
        'ha.zookeeper.parent-znode': '/{}/hadoop-ha'.format(sdk_utils.get_zk_path(
            foldered_name))
    })

    hdfs_site = etree.fromstring(sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'endpoints hdfs-site.xml'))
    expect = {
        'dfs.namenode.shared.edits.dir': 'qjournal://{}/hdfs'.format(';'.join([
            sdk_hosts.autoip_host(
                foldered_name,
                'journal-{}-node'.format(i),
                8485
            ) for i in range(3)])),
    }
    for i in range(2):
        name_node = 'name-{}-node'.format(i)
        expect['dfs.namenode.rpc-address.hdfs.{}'.format(name_node)] = sdk_hosts.autoip_host(
            foldered_name, name_node, 9001)
        expect['dfs.namenode.http-address.hdfs.{}'.format(name_node)] = sdk_hosts.autoip_host(
            foldered_name, name_node, 9002)
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
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal-0')
    name_ids = sdk_tasks.get_task_ids(foldered_name, 'name')
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data')

    sdk_tasks.kill_task_with_pattern('journalnode', sdk_hosts.system_host(foldered_name, 'journal-0-node'))
    config.expect_recovery(service_name=foldered_name)
    sdk_tasks.check_tasks_updated(foldered_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'name', name_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_name_node():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    name_ids = sdk_tasks.get_task_ids(foldered_name, 'name-0')
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data')

    sdk_tasks.kill_task_with_pattern('namenode', sdk_hosts.system_host(foldered_name, 'name-0-node'))
    config.expect_recovery(service_name=foldered_name)
    sdk_tasks.check_tasks_updated(foldered_name, 'name', name_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_data_node():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data-0')
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    name_ids = sdk_tasks.get_task_ids(foldered_name, 'name')

    sdk_tasks.kill_task_with_pattern('datanode', sdk_hosts.system_host(foldered_name, 'data-0-node'))
    config.expect_recovery(service_name=foldered_name)
    sdk_tasks.check_tasks_updated(foldered_name, 'data', data_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'name', name_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_scheduler():
    sdk_tasks.kill_task_with_pattern('hdfs.scheduler.Main', shakedown.get_service_ips('marathon').pop())
    config.check_healthy(service_name=sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_journalnodes():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    data_ids = sdk_tasks.get_task_ids(sdk_utils.get_foldered_name(config.SERVICE_NAME), 'data')

    for journal_pod in config.get_pod_type_instances("journal", foldered_name):
        sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod restart {}'.format(journal_pod))

    config.expect_recovery(service_name=foldered_name)

    # name nodes fail and restart, so don't check those
    sdk_tasks.check_tasks_updated(foldered_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_namenodes():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    name_ids = sdk_tasks.get_task_ids(foldered_name, 'name')
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data')

    for name_pod in config.get_pod_type_instances("name", foldered_name):
        sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod restart {}'.format(name_pod))

    config.expect_recovery(service_name=foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, 'name', name_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_datanodes():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    name_ids = sdk_tasks.get_task_ids(foldered_name, 'name')
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data')

    for data_pod in config.get_pod_type_instances("data", foldered_name):
        sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod restart {}'.format(data_pod))

    config.expect_recovery(service_name=foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, 'data', data_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'name', name_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanently_replace_namenodes():
    replace_name_node(0)
    replace_name_node(1)
    replace_name_node(0)


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanent_and_transient_namenode_failures_0_1():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_healthy(service_name=foldered_name)
    name_0_ids = sdk_tasks.get_task_ids(foldered_name, 'name-0')
    name_1_ids = sdk_tasks.get_task_ids(foldered_name, 'name-1')
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data')

    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod replace name-0')
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod restart name-1')

    config.expect_recovery(service_name=foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, 'name-0', name_0_ids)
    sdk_tasks.check_tasks_updated(foldered_name, 'name-1', name_1_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanent_and_transient_namenode_failures_1_0():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_healthy(service_name=foldered_name)
    name_0_ids = sdk_tasks.get_task_ids(foldered_name, 'name-0')
    name_1_ids = sdk_tasks.get_task_ids(foldered_name, 'name-1')
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data')

    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod replace name-1')
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod restart name-0')

    config.expect_recovery(service_name=foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, 'name-0', name_0_ids)
    sdk_tasks.check_tasks_updated(foldered_name, 'name-1', name_1_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'data', data_ids)


@pytest.mark.smoke
def test_install():
    config.check_healthy(service_name=sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.sanity
def test_bump_journal_cpus():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    name_ids = sdk_tasks.get_task_ids(foldered_name, 'name')
    log.info('journal ids: ' + str(journal_ids))

    sdk_marathon.bump_cpu_count_config(foldered_name, 'JOURNAL_CPUS')

    sdk_tasks.check_tasks_updated(foldered_name, 'journal', journal_ids)
    # journal node update should not cause any of the name nodes to crash
    # if the name nodes crashed, then it implies the journal nodes were updated in parallel, when they should've been updated serially
    # for journal nodes, the deploy plan is parallel, while the update plan is serial. maybe the deploy plan was mistakenly used?
    sdk_tasks.check_tasks_not_updated(foldered_name, 'name', name_ids)
    config.check_healthy(service_name=foldered_name)


@pytest.mark.sanity
def test_bump_data_nodes():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data')
    log.info('data ids: ' + str(data_ids))

    sdk_marathon.bump_task_count_config(foldered_name, 'DATA_COUNT')

    config.check_healthy(service_name=foldered_name, count=config.DEFAULT_TASK_COUNT + 1)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'data', data_ids)


@pytest.mark.readiness_check
@pytest.mark.sanity
def test_modify_app_config():
    """This tests checks that the modification of the app config does not trigger a recovery."""
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    sdk_plan.wait_for_completed_recovery(foldered_name)
    old_recovery_plan = sdk_plan.get_plan(foldered_name, "recovery")

    app_config_field = 'TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_EXPIRY_MS'
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    name_ids = sdk_tasks.get_task_ids(foldered_name, 'name')
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data')

    marathon_config = sdk_marathon.get_config(foldered_name)
    log.info('marathon config: ')
    log.info(marathon_config)
    expiry_ms = int(marathon_config['env'][app_config_field])
    marathon_config['env'][app_config_field] = str(expiry_ms + 1)
    sdk_marathon.update_app(foldered_name, marathon_config, timeout=15 * 60)

    # All tasks should be updated because hdfs-site.xml has changed
    config.check_healthy(service_name=foldered_name)
    sdk_tasks.check_tasks_updated(foldered_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_updated(foldered_name, 'name', name_ids)
    sdk_tasks.check_tasks_updated(foldered_name, 'data', data_ids)

    sdk_plan.wait_for_completed_recovery(foldered_name)
    new_recovery_plan = sdk_plan.get_plan(foldered_name, "recovery")
    assert old_recovery_plan == new_recovery_plan


@pytest.mark.sanity
def test_modify_app_config_rollback():
    app_config_field = 'TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_EXPIRY_MS'
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)

    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data')

    old_config = sdk_marathon.get_config(foldered_name)
    marathon_config = sdk_marathon.get_config(foldered_name)
    log.info('marathon config: ')
    log.info(marathon_config)
    expiry_ms = int(marathon_config['env'][app_config_field])
    log.info('expiry ms: ' + str(expiry_ms))
    marathon_config['env'][app_config_field] = str(expiry_ms + 1)
    sdk_marathon.update_app(foldered_name, marathon_config, timeout=15 * 60)

    # Wait for journal nodes to be affected by the change
    sdk_tasks.check_tasks_updated(foldered_name, 'journal', journal_ids)
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')

    log.info('old config: ')
    log.info(old_config)
    # Put the old config back (rollback)
    sdk_marathon.update_app(foldered_name, old_config)

    # Wait for the journal nodes to return to their old configuration
    sdk_tasks.check_tasks_updated(foldered_name, 'journal', journal_ids)
    config.check_healthy(service_name=foldered_name)

    marathon_config = sdk_marathon.get_config(foldered_name)
    assert int(marathon_config['env'][app_config_field]) == expiry_ms

    # Data tasks should not have been affected
    sdk_tasks.check_tasks_not_updated(foldered_name, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.metrics
@pytest.mark.dcos_min_version('1.9')
def test_metrics():
    expected_metrics = [
        "JournalNode.jvm.JvmMetrics.ThreadsRunnable",
        "null.rpc.rpc.RpcQueueTimeNumOps",
        "null.metricssystem.MetricsSystem.PublishAvgTime"
    ]

    def expected_metrics_exist(emitted_metrics):
        # HDFS metric names need sanitation as they're dynamic.
        # For eg: ip-10-0-0-139.null.rpc.rpc.RpcQueueTimeNumOps
        # This is consistent across all HDFS metric names.
        metric_names = set(['.'.join(metric_name.split(".")[1:]) for metric_name in emitted_metrics])
        return sdk_metrics.check_metrics_presence(metric_names, expected_metrics)

    sdk_metrics.wait_for_service_metrics(
        config.PACKAGE_NAME,
        sdk_utils.get_foldered_name(config.SERVICE_NAME),
        "journal-0-node",
        config.DEFAULT_HDFS_TIMEOUT,
        expected_metrics_exist
    )


def replace_name_node(index):
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_healthy(service_name=foldered_name)
    name_node_name = 'name-' + str(index)
    name_id = sdk_tasks.get_task_ids(foldered_name, name_node_name)
    journal_ids = sdk_tasks.get_task_ids(foldered_name, 'journal')
    data_ids = sdk_tasks.get_task_ids(foldered_name, 'data')

    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod replace {}'.format(name_node_name))

    config.expect_recovery(service_name=foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, name_node_name, name_id)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, 'data', data_ids)
