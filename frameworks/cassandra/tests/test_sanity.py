import json
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

WRITE_DATA_JOB = config.get_write_data_job(node_address=config.FOLDERED_NODE_ADDRESS)
VERIFY_DATA_JOB = config.get_verify_data_job(node_address=config.FOLDERED_NODE_ADDRESS)
DELETE_DATA_JOB = config.get_delete_data_job(node_address=config.FOLDERED_NODE_ADDRESS)
VERIFY_DELETION_JOB = config.get_verify_deletion_job(node_address=config.FOLDERED_NODE_ADDRESS)
TEST_JOBS = [WRITE_DATA_JOB, VERIFY_DATA_JOB, DELETE_DATA_JOB, VERIFY_DELETION_JOB]


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.FOLDERED_SERVICE_NAME, package_name=config.PACKAGE_NAME)
        sdk_upgrade.test_upgrade(
            "beta-{}".format(config.PACKAGE_NAME),
            config.PACKAGE_NAME,
            config.DEFAULT_TASK_COUNT,
            service_name=config.FOLDERED_SERVICE_NAME,
            additional_options={"service": {"name": config.FOLDERED_SERVICE_NAME} })

        tmp_dir = tempfile.mkdtemp(prefix='cassandra-test')
        for job in TEST_JOBS:
            sdk_jobs.install_job(job, tmp_dir=tmp_dir)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.FOLDERED_SERVICE_NAME, package_name=config.PACKAGE_NAME)

        for job in TEST_JOBS:
            sdk_jobs.remove_job(job)


@pytest.mark.sanity
@pytest.mark.smoke
def test_service_health():
    assert shakedown.service_healthy(config.FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
def test_endpoints():
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    endpoints = json.loads(cmd.run_cli('cassandra --name={} endpoints node'.format(config.FOLDERED_SERVICE_NAME)))
    assert endpoints['dns'][0] == sdk_hosts.autoip_host(config.FOLDERED_SERVICE_NAME, 'node-0-server', 9042)
    assert endpoints['vip'] == sdk_hosts.vip_host(config.FOLDERED_SERVICE_NAME, 'node', 9042)


@pytest.mark.sanity
@pytest.mark.smoke
def test_repair_cleanup_plans_complete():
    parameters = {'CASSANDRA_KEYSPACE': 'testspace1'}

    # populate 'testspace1' for test, then delete afterwards:
    with sdk_jobs.RunJobContext(
        before_jobs=[WRITE_DATA_JOB, VERIFY_DATA_JOB],
        after_jobs=[DELETE_DATA_JOB, VERIFY_DELETION_JOB]):

        sdk_plan.start_plan(config.FOLDERED_SERVICE_NAME, 'cleanup', parameters=parameters)
        sdk_plan.wait_for_completed_plan(config.FOLDERED_SERVICE_NAME, 'cleanup')

        sdk_plan.start_plan(config.FOLDERED_SERVICE_NAME, 'repair', parameters=parameters)
        sdk_plan.wait_for_completed_plan(config.FOLDERED_SERVICE_NAME, 'repair')


@pytest.mark.sanity
@pytest.mark.metrics
@sdk_utils.dcos_1_9_or_higher
def test_metrics():
    sdk_metrics.wait_for_any_metrics(config.FOLDERED_SERVICE_NAME, "node-0-server", config.DEFAULT_CASSANDRA_TIMEOUT)
