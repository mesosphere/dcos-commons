import os
import tempfile
import pytest

import shakedown

import sdk_install as install
import sdk_plan as plan
import sdk_jobs as jobs
import sdk_utils as utils
import sdk_networks as networks

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    DEFAULT_NODE_TASKS,
    get_write_data_job,
    get_verify_data_job,
    get_delete_data_job,
    get_verify_deletion_job
)


WRITE_DATA_JOB = get_write_data_job()
VERIFY_DATA_JOB = get_verify_data_job()
DELETE_DATA_JOB = get_delete_data_job()
VERIFY_DELETION_JOB = get_verify_deletion_job()
TEST_JOBS = [WRITE_DATA_JOB, VERIFY_DATA_JOB, DELETE_DATA_JOB, VERIFY_DELETION_JOB]

overlay_nostrict = pytest.mark.skipif(os.environ.get("SECURITY") == "strict",
    reason="overlay tests currently broken in strict")


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False,
                    additional_options=networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    tmp_dir = tempfile.mkdtemp(prefix='cassandra-test')
    for job in TEST_JOBS:
        jobs.install_job(job, tmp_dir=tmp_dir)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)

    for job in TEST_JOBS:
        jobs.remove_job(job)



@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
@overlay_nostrict
@utils.dcos_1_9_or_higher
def test_service_overlay_health():
    shakedown.service_healthy(PACKAGE_NAME)
    for task in DEFAULT_NODE_TASKS:
        networks.check_task_network(task)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
@overlay_nostrict
@utils.dcos_1_9_or_higher
def test_functionality():
    parameters = {'CASSANDRA_KEYSPACE': 'testspace1'}

    # populate 'testspace1' for test, then delete afterwards:
    with jobs.RunJobContext(
        before_jobs=[WRITE_DATA_JOB, VERIFY_DATA_JOB],
        after_jobs=[DELETE_DATA_JOB, VERIFY_DELETION_JOB]):

        plan.start_plan(PACKAGE_NAME, 'cleanup', parameters=parameters)
        plan.wait_for_completed_plan(PACKAGE_NAME, 'cleanup')

        plan.start_plan(PACKAGE_NAME, 'repair', parameters=parameters)
        plan.wait_for_completed_plan(PACKAGE_NAME, 'repair')


@pytest.mark.sanity
@pytest.mark.overlay
@overlay_nostrict
@utils.dcos_1_9_or_higher
def test_endpoints():
    endpoints = networks.get_and_test_endpoints("", PACKAGE_NAME, 1)  # tests that the correct number of endpoints are found, should just be "node"
    assert "node" in endpoints, "Cassandra endpoints should contain only 'node', got {}".format(endpoints)
    endpoints = networks.get_and_test_endpoints("node", PACKAGE_NAME, 4)
    assert "address" in endpoints, "Endpoints missing address key"
    networks.check_endpoints_on_overlay(endpoints)
