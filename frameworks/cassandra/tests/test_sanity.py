import tempfile

import pytest
import sdk_cmd as cmd
import sdk_hosts
import sdk_install
import sdk_jobs
import sdk_metrics
import sdk_plan
import sdk_upgrade
import sdk_utils
import shakedown
from tests import config


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    test_jobs = []
    try:
        test_jobs = config.get_all_jobs(node_address=config.get_foldered_node_address())
        # destroy any leftover jobs first, so that they don't touch the newly installed service:
        for job in test_jobs:
            sdk_jobs.remove_job(job)

        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())
        sdk_upgrade.test_upgrade(
            config.PACKAGE_NAME,
            config.get_foldered_service_name(),
            config.DEFAULT_TASK_COUNT,
            additional_options={"service": {"name": config.get_foldered_service_name()}})

        tmp_dir = tempfile.mkdtemp(prefix='cassandra-test')
        for job in test_jobs:
            sdk_jobs.install_job(job, tmp_dir=tmp_dir)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())

        for job in test_jobs:
            sdk_jobs.remove_job(job)


@pytest.mark.sanity
@pytest.mark.smoke
def test_service_health():
    assert shakedown.service_healthy(config.get_foldered_service_name())


@pytest.mark.sanity
def test_endpoints():
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    endpoints = cmd.svc_cli(
        config.PACKAGE_NAME, config.get_foldered_service_name(),
        'endpoints native-client', json=True)
    assert endpoints['dns'][0] == sdk_hosts.autoip_host(
        config.get_foldered_service_name(), 'node-0-server', 9042)
    assert not 'vip' in endpoints


@pytest.mark.sanity
@pytest.mark.smoke
def test_repair_cleanup_plans_complete():
    parameters = {'CASSANDRA_KEYSPACE': 'testspace1'}

    # populate 'testspace1' for test, then delete afterwards:
    with sdk_jobs.RunJobContext(
            before_jobs=[
                config.get_write_data_job(
                    node_address=config.get_foldered_node_address()),
                config.get_verify_data_job(
                    node_address=config.get_foldered_node_address())
            ],
            after_jobs=[
                config.get_delete_data_job(
                    node_address=config.get_foldered_node_address()),
                config.get_verify_deletion_job(
                    node_address=config.get_foldered_node_address())
            ]):

        sdk_plan.start_plan(
            config.get_foldered_service_name(), 'cleanup', parameters=parameters)
        sdk_plan.wait_for_completed_plan(
            config.get_foldered_service_name(), 'cleanup')

        sdk_plan.start_plan(
            config.get_foldered_service_name(), 'repair', parameters=parameters)
        sdk_plan.wait_for_completed_plan(
            config.get_foldered_service_name(), 'repair')


@pytest.mark.sanity
@pytest.mark.metrics
@sdk_utils.dcos_1_9_or_higher
def test_metrics():
    sdk_metrics.wait_for_service_metrics(
        config.PACKAGE_NAME,
        config.get_foldered_service_name(),
        "node-0-server",
        config.DEFAULT_CASSANDRA_TIMEOUT,
        config.EXPECTED_METRICS
    )
