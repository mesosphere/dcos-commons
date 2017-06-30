import json
import pytest
import shakedown
import tempfile
import uuid

from tests.config import *
import sdk_cmd as cmd
import sdk_hosts as hosts
import sdk_install as install
import sdk_jobs as jobs
import sdk_plan as plan
import sdk_utils as utils


WRITE_DATA_JOB = get_write_data_job(node_address=FOLDERED_NODE_ADDRESS)
VERIFY_DATA_JOB = get_verify_data_job(node_address=FOLDERED_NODE_ADDRESS)
DELETE_DATA_JOB = get_delete_data_job(node_address=FOLDERED_NODE_ADDRESS)
VERIFY_DELETION_JOB = get_verify_deletion_job(node_address=FOLDERED_NODE_ADDRESS)
TEST_JOBS = [WRITE_DATA_JOB, VERIFY_DATA_JOB, DELETE_DATA_JOB, VERIFY_DELETION_JOB]
FOLDERED_SERVICE_NAME = utils.get_foldered_name(PACKAGE_NAME)


def setup_module(module):
    install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(
        PACKAGE_NAME,
        DEFAULT_TASK_COUNT,
        service_name=FOLDERED_SERVICE_NAME,
        additional_options={"service": { "name": FOLDERED_SERVICE_NAME } },
        check_suppression=False)
    plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME)

    tmp_dir = tempfile.mkdtemp(prefix='cassandra-test')
    for job in TEST_JOBS:
        jobs.install_job(job, tmp_dir=tmp_dir)


def teardown_module(module):
    install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)

    # remove job definitions from metronome
    for job in TEST_JOBS:
        jobs.remove_job(job)


@pytest.mark.sanity
@pytest.mark.smoke
def test_service_health():
    assert shakedown.service_healthy(FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
def test_endpoints():
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    endpoints = json.loads(cmd.run_cli('cassandra --name={} endpoints node'.format(FOLDERED_SERVICE_NAME)))
    assert endpoints['dns'][0] == hosts.autoip_host(FOLDERED_SERVICE_NAME, 'node-0-server', 9042)
    assert endpoints['vips'][0] == hosts.vip_host(FOLDERED_SERVICE_NAME, 'node', 9042)


@pytest.mark.sanity
@pytest.mark.smoke
def test_repair_cleanup_plans_complete():
    parameters = {'CASSANDRA_KEYSPACE': 'testspace1'}

    # populate 'testspace1' for test, then delete afterwards:
    with jobs.RunJobContext(
        before_jobs=[WRITE_DATA_JOB, VERIFY_DATA_JOB],
        after_jobs=[DELETE_DATA_JOB, VERIFY_DELETION_JOB]):

        plan.start_plan(FOLDERED_SERVICE_NAME, 'cleanup', parameters=parameters)
        plan.wait_for_completed_plan(FOLDERED_SERVICE_NAME, 'cleanup')

        plan.start_plan(FOLDERED_SERVICE_NAME, 'repair', parameters=parameters)
        plan.wait_for_completed_plan(FOLDERED_SERVICE_NAME, 'repair')

