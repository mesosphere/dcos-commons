import logging
import xml.etree.ElementTree as etree
from typing import Iterator, List

import pytest
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_metrics
import sdk_networks
import sdk_plan
import sdk_recovery
import sdk_tasks
import sdk_upgrade
import sdk_utils

from tests import config

log = logging.getLogger(__name__)

foldered_name = config.FOLDERED_SERVICE_NAME


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security: None) -> Iterator[None]:
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        if sdk_utils.dcos_version_less_than("1.9"):
            # HDFS upgrade in 1.8 is not supported.
            sdk_install.install(
                config.PACKAGE_NAME,
                foldered_name,
                config.DEFAULT_TASK_COUNT,
                additional_options={"service": {"name": foldered_name}},
                timeout_seconds=30 * 60,
            )
        else:
            sdk_upgrade.test_upgrade(
                config.PACKAGE_NAME,
                foldered_name,
                config.DEFAULT_TASK_COUNT,
                additional_options={"service": {"name": foldered_name}},
                timeout_seconds=30 * 60,
            )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.fixture(autouse=True)
def pre_test_setup() -> None:
    config.check_healthy(service_name=foldered_name)


@pytest.mark.sanity
def test_endpoints() -> None:
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    core_site = etree.fromstring(
        sdk_networks.get_endpoint_string(config.PACKAGE_NAME, foldered_name, "core-site.xml")
    )
    check_properties(
        core_site,
        {"ha.zookeeper.parent-znode": "/{}/hadoop-ha".format(sdk_utils.get_zk_path(foldered_name))},
    )

    hdfs_site = etree.fromstring(
        sdk_networks.get_endpoint_string(config.PACKAGE_NAME, foldered_name, "hdfs-site.xml")
    )
    expect = {
        "dfs.namenode.shared.edits.dir": "qjournal://{}/hdfs".format(
            ";".join(
                [
                    sdk_hosts.autoip_host(foldered_name, "journal-{}-node".format(i), 8485)
                    for i in range(3)
                ]
            )
        )
    }
    for i in range(2):
        name_node = "name-{}-node".format(i)
        expect["dfs.namenode.rpc-address.hdfs.{}".format(name_node)] = sdk_hosts.autoip_host(
            foldered_name, name_node, 9001
        )
        expect["dfs.namenode.http-address.hdfs.{}".format(name_node)] = sdk_hosts.autoip_host(
            foldered_name, name_node, 9002
        )
    check_properties(hdfs_site, expect)


def check_properties(xml, expect) -> None:
    found = {}
    for prop in xml.findall("property"):
        name = prop.find("name").text
        if name in expect:
            found[name] = prop.find("value").text
    log.info("expect: {}\nfound:  {}".format(expect, found))
    assert expect == found


@pytest.mark.recovery
def test_kill_journal_node() -> None:
    journal_task = sdk_tasks.get_service_tasks(foldered_name, "journal-0")[0]
    name_ids = sdk_tasks.get_task_ids(foldered_name, "name")
    data_ids = sdk_tasks.get_task_ids(foldered_name, "data")

    sdk_cmd.kill_task_with_pattern("journalnode", "nobody", agent_host=journal_task.host)

    config.expect_recovery(service_name=foldered_name)
    sdk_tasks.check_tasks_updated(foldered_name, "journal", [journal_task.id])
    sdk_tasks.check_tasks_not_updated(foldered_name, "name", name_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "data", data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_name_node() -> None:
    name_task = sdk_tasks.get_service_tasks(foldered_name, "name-0")[0]
    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")
    data_ids = sdk_tasks.get_task_ids(foldered_name, "data")

    sdk_cmd.kill_task_with_pattern("namenode", "nobody", agent_host=name_task.host)

    config.expect_recovery(service_name=foldered_name)
    sdk_tasks.check_tasks_updated(foldered_name, "name", [name_task.id])
    sdk_tasks.check_tasks_not_updated(foldered_name, "journal", journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "data", data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_data_node() -> None:
    data_task = sdk_tasks.get_service_tasks(foldered_name, "data-0")[0]
    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")
    name_ids = sdk_tasks.get_task_ids(foldered_name, "name")

    sdk_cmd.kill_task_with_pattern("datanode", "nobody", agent_host=data_task.host)

    config.expect_recovery(service_name=foldered_name)
    sdk_tasks.check_tasks_updated(foldered_name, "data", [data_task.id])
    sdk_tasks.check_tasks_not_updated(foldered_name, "journal", journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "name", name_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_scheduler() -> None:
    task_ids = sdk_tasks.get_task_ids(foldered_name, "")
    scheduler_task_prefix = sdk_marathon.get_scheduler_task_prefix(foldered_name)
    scheduler_ids = sdk_tasks.get_task_ids("marathon", scheduler_task_prefix)
    assert len(scheduler_ids) == 1, "Expected to find one scheduler task"

    sdk_cmd.kill_task_with_pattern(
        "./hdfs-scheduler/bin/hdfs",
        "nobody",
        agent_host=sdk_marathon.get_scheduler_host(foldered_name),
    )

    # scheduler should be restarted, but service tasks should be left as-is:
    sdk_tasks.check_tasks_updated("marathon", scheduler_task_prefix, scheduler_ids)
    sdk_tasks.wait_for_active_framework(foldered_name)
    sdk_tasks.check_tasks_not_updated(foldered_name, "", task_ids)
    config.check_healthy(service_name=foldered_name)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_journalnodes() -> None:
    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")
    data_ids = sdk_tasks.get_task_ids(foldered_name, "data")

    for journal_pod in config.get_pod_type_instances("journal", foldered_name):
        sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod restart {}".format(journal_pod))

    config.expect_recovery(service_name=foldered_name)

    # name nodes fail and restart, so don't check those
    sdk_tasks.check_tasks_updated(foldered_name, "journal", journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "data", data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_namenodes() -> None:
    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")
    name_ids = sdk_tasks.get_task_ids(foldered_name, "name")
    data_ids = sdk_tasks.get_task_ids(foldered_name, "data")

    for name_pod in config.get_pod_type_instances("name", foldered_name):
        sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod restart {}".format(name_pod))

    config.expect_recovery(service_name=foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, "name", name_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "journal", journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "data", data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_datanodes() -> None:
    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")
    name_ids = sdk_tasks.get_task_ids(foldered_name, "name")
    data_ids = sdk_tasks.get_task_ids(foldered_name, "data")

    for data_pod in config.get_pod_type_instances("data", foldered_name):
        sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod restart {}".format(data_pod))

    config.expect_recovery(service_name=foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, "data", data_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "journal", journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "name", name_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanent_and_transient_namenode_failures_0_1() -> None:
    config.check_healthy(service_name=foldered_name)
    name_0_ids = sdk_tasks.get_task_ids(foldered_name, "name-0")
    name_1_ids = sdk_tasks.get_task_ids(foldered_name, "name-1")
    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")
    data_ids = sdk_tasks.get_task_ids(foldered_name, "data")

    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod replace name-0")
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod restart name-1")

    config.expect_recovery(service_name=foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, "name-0", name_0_ids)
    sdk_tasks.check_tasks_updated(foldered_name, "name-1", name_1_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "journal", journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "data", data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanent_and_transient_namenode_failures_1_0() -> None:
    config.check_healthy(service_name=foldered_name)
    name_0_ids = sdk_tasks.get_task_ids(foldered_name, "name-0")
    name_1_ids = sdk_tasks.get_task_ids(foldered_name, "name-1")
    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")
    data_ids = sdk_tasks.get_task_ids(foldered_name, "data")

    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod replace name-1")
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod restart name-0")

    config.expect_recovery(service_name=foldered_name)

    sdk_tasks.check_tasks_updated(foldered_name, "name-0", name_0_ids)
    sdk_tasks.check_tasks_updated(foldered_name, "name-1", name_1_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "journal", journal_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "data", data_ids)


@pytest.mark.smoke
def test_install() -> None:
    config.check_healthy(service_name=foldered_name)


@pytest.mark.sanity
def test_bump_journal_cpus() -> None:
    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")
    name_ids = sdk_tasks.get_task_ids(foldered_name, "name")
    log.info("journal ids: " + str(journal_ids))

    sdk_marathon.bump_cpu_count_config(foldered_name, "JOURNAL_CPUS")

    sdk_tasks.check_tasks_updated(foldered_name, "journal", journal_ids)
    # journal node update should not cause any of the name nodes to crash
    # if the name nodes crashed, then it implies the journal nodes were updated in parallel, when they should've been updated serially
    # for journal nodes, the deploy plan is parallel, while the update plan is serial. maybe the deploy plan was mistakenly used?
    sdk_tasks.check_tasks_not_updated(foldered_name, "name", name_ids)
    config.check_healthy(service_name=foldered_name)


@pytest.mark.sanity
def test_bump_data_nodes() -> None:
    data_ids = sdk_tasks.get_task_ids(foldered_name, "data")
    log.info("data ids: " + str(data_ids))

    sdk_marathon.bump_task_count_config(foldered_name, "DATA_COUNT")

    config.check_healthy(service_name=foldered_name, count=config.DEFAULT_TASK_COUNT + 1)
    sdk_tasks.check_tasks_not_updated(foldered_name, "data", data_ids)


@pytest.mark.readiness_check
@pytest.mark.sanity
def test_modify_app_config() -> None:
    """This tests checks that the modification of the app config does not trigger a recovery."""
    sdk_plan.wait_for_completed_recovery(foldered_name)
    old_recovery_plan = sdk_plan.get_plan(foldered_name, "recovery")

    app_config_field = "TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_EXPIRY_MS"
    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")
    name_ids = sdk_tasks.get_task_ids(foldered_name, "name")
    data_ids = sdk_tasks.get_task_ids(foldered_name, "data")

    marathon_config = sdk_marathon.get_config(foldered_name)
    log.info("marathon config: ")
    log.info(marathon_config)
    expiry_ms = int(marathon_config["env"][app_config_field])
    marathon_config["env"][app_config_field] = str(expiry_ms + 1)
    sdk_marathon.update_app(marathon_config, timeout=15 * 60)

    # All tasks should be updated because hdfs-site.xml has changed
    config.check_healthy(service_name=foldered_name)
    sdk_tasks.check_tasks_updated(foldered_name, "journal", journal_ids)
    sdk_tasks.check_tasks_updated(foldered_name, "name", name_ids)
    sdk_tasks.check_tasks_updated(foldered_name, "data", data_ids)

    sdk_plan.wait_for_completed_recovery(foldered_name)
    new_recovery_plan = sdk_plan.get_plan(foldered_name, "recovery")
    assert old_recovery_plan == new_recovery_plan


@pytest.mark.sanity
def test_modify_app_config_rollback() -> None:
    app_config_field = "TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_EXPIRY_MS"

    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")
    data_ids = sdk_tasks.get_task_ids(foldered_name, "data")

    old_config = sdk_marathon.get_config(foldered_name)
    marathon_config = sdk_marathon.get_config(foldered_name)
    log.info("marathon config: ")
    log.info(marathon_config)
    expiry_ms = int(marathon_config["env"][app_config_field])
    log.info("expiry ms: " + str(expiry_ms))
    marathon_config["env"][app_config_field] = str(expiry_ms + 1)
    sdk_marathon.update_app(marathon_config, timeout=15 * 60)

    # Wait for journal nodes to be affected by the change
    sdk_tasks.check_tasks_updated(foldered_name, "journal", journal_ids)
    journal_ids = sdk_tasks.get_task_ids(foldered_name, "journal")

    log.info("old config: ")
    log.info(old_config)
    # Put the old config back (rollback)
    sdk_marathon.update_app(old_config)

    # Wait for the journal nodes to return to their old configuration
    sdk_tasks.check_tasks_updated(foldered_name, "journal", journal_ids)
    config.check_healthy(service_name=foldered_name)

    marathon_config = sdk_marathon.get_config(foldered_name)
    assert int(marathon_config["env"][app_config_field]) == expiry_ms

    # Data tasks should not have been affected
    sdk_tasks.check_tasks_not_updated(foldered_name, "data", data_ids)


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.9")
def test_metrics() -> None:
    expected_metrics = [
        "JournalNode.jvm.JvmMetrics.ThreadsRunnable",
        "null.rpc.rpc.RpcQueueTimeNumOps",
        "null.metricssystem.MetricsSystem.PublishAvgTime",
    ]

    def expected_metrics_exist(emitted_metrics: List[str]) -> bool:
        # HDFS metric names need sanitation as they're dynamic.
        # For eg: ip-10-0-0-139.null.rpc.rpc.RpcQueueTimeNumOps
        # This is consistent across all HDFS metric names.
        metric_names = set(
            [".".join(metric_name.split(".")[1:]) for metric_name in emitted_metrics]
        )
        return sdk_metrics.check_metrics_presence(list(metric_names), list(expected_metrics))

    sdk_metrics.wait_for_service_metrics(
        config.PACKAGE_NAME,
        foldered_name,
        "journal-0",
        "journal-0-node",
        config.DEFAULT_HDFS_TIMEOUT,
        expected_metrics_exist,
    )


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanently_replace_namenodes() -> None:
    pod_list = ["name-0", "name-1", "name-0"]
    for pod in pod_list:
        sdk_recovery.check_permanent_recovery(
            config.PACKAGE_NAME, foldered_name, pod, recovery_timeout_s=25 * 60
        )


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanently_replace_journalnodes() -> None:
    pod_list = ["journal-0", "journal-1", "journal-2"]
    for pod in pod_list:
        sdk_recovery.check_permanent_recovery(
            config.PACKAGE_NAME, foldered_name, pod, recovery_timeout_s=25 * 60
        )


@pytest.mark.sanity
@pytest.mark.recovery
def test_namenodes_acheive_quorum_after_journalnode_replace() -> None:
    """
    This test aims to check that namenodes recover after a journalnode failure.
    It checks the fix to this issue works: https://jira.apache.org/jira/browse/HDFS-10659.
    After the first Journal Node recovery, the second Journal Node pod replace triggers
    crash looping of both replaced Journal Node pod and all NameNode pods.
    """

    pod_list = ["journal-0", "journal-1", "journal-0"]
    for pod in pod_list:
        sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod replace {}".format(pod))

        # waiting for recovery to start first before it completes to avoid timing issues
        sdk_plan.wait_for_in_progress_recovery(service_name=foldered_name, timeout_seconds=5 * 60)

        # sdk_plan.wait_for_completed_recovery includes tracking of failed tasks and will
        # terminate in case of a crash loop
        sdk_plan.wait_for_completed_recovery(service_name=foldered_name, timeout_seconds=5 * 60)
