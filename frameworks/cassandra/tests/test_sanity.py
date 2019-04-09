import pytest
import logging
from typing import Any, Dict, Iterator, List

import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_jobs
import sdk_metrics
import sdk_networks
import sdk_plan
import sdk_service
import sdk_tasks
import sdk_upgrade

from tests import config


log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security: None) -> Iterator[None]:
    test_jobs: List[Dict[str, Any]] = []
    try:
        test_jobs = config.get_all_jobs(node_address=config.get_foldered_node_address())
        # destroy/reinstall any prior leftover jobs, so that they don't touch the newly installed service:
        for job in test_jobs:
            sdk_jobs.install_job(job)

        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())
        sdk_upgrade.test_upgrade(
            config.PACKAGE_NAME,
            config.get_foldered_service_name(),
            config.DEFAULT_TASK_COUNT,
            from_options={"service": {"name": config.get_foldered_service_name()}},
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())

        for job in test_jobs:
            sdk_jobs.remove_job(job)


@pytest.mark.sanity
def test_endpoints() -> None:
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    endpoints = sdk_networks.get_endpoint(
        config.PACKAGE_NAME, config.get_foldered_service_name(), "native-client"
    )
    assert endpoints["dns"][0] == sdk_hosts.autoip_host(
        config.get_foldered_service_name(), "node-0-server", 9042
    )
    assert "vip" not in endpoints


@pytest.mark.sanity
@pytest.mark.smoke
def test_repair_cleanup_plans_complete() -> None:
    parameters = {"CASSANDRA_KEYSPACE": "testspace1"}

    # populate 'testspace1' for test, then delete afterwards:
    with sdk_jobs.RunJobContext(
        before_jobs=[
            config.get_write_data_job(node_address=config.get_foldered_node_address()),
            config.get_verify_data_job(node_address=config.get_foldered_node_address()),
        ],
        after_jobs=[
            config.get_delete_data_job(node_address=config.get_foldered_node_address()),
            config.get_verify_deletion_job(node_address=config.get_foldered_node_address()),
        ],
    ):

        sdk_plan.start_plan(config.get_foldered_service_name(), "cleanup", parameters=parameters)
        sdk_plan.wait_for_completed_plan(config.get_foldered_service_name(), "cleanup")

        sdk_plan.start_plan(config.get_foldered_service_name(), "repair", parameters=parameters)
        sdk_plan.wait_for_completed_plan(config.get_foldered_service_name(), "repair")


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.9")
def test_metrics() -> None:
    expected_metrics = [
        "org.apache.cassandra.metrics.Table.CoordinatorReadLatency.system.hints.p999",
        "org.apache.cassandra.metrics.Table.CompressionRatio.system_schema.indexes",
        "org.apache.cassandra.metrics.ThreadPools.ActiveTasks.internal.MemtableReclaimMemory",
    ]

    def expected_metrics_exist(emitted_metrics: List[str]) -> bool:
        return sdk_metrics.check_metrics_presence(
            emitted_metrics=emitted_metrics,
            expected_metrics=expected_metrics,
        )

    sdk_metrics.wait_for_service_metrics(
        config.PACKAGE_NAME,
        config.get_foldered_service_name(),
        "node-0",
        "node-0-server",
        config.DEFAULT_CASSANDRA_TIMEOUT,
        expected_metrics_exist,
    )


@pytest.mark.sanity
def test_custom_jmx_port() -> None:
    expected_open_port = ":7200 (LISTEN)"

    new_config = {"cassandra": {"jmx_port": 7200}}

    sdk_service.update_configuration(
        config.PACKAGE_NAME,
        config.get_foldered_service_name(),
        new_config,
        config.DEFAULT_TASK_COUNT,
    )

    sdk_plan.wait_for_completed_deployment(config.get_foldered_service_name())

    tasks = sdk_tasks.get_service_tasks(config.get_foldered_service_name(), "node")

    for task in tasks:
        _, stdout, _ = sdk_cmd.run_cli("task exec {} lsof -i :7200".format(task.id))
        assert expected_open_port in stdout
