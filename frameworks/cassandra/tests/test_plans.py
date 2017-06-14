import pytest
import shakedown
import tempfile
import uuid

from tests.config import *
import sdk_install as install
import sdk_jobs as jobs
import sdk_plan as plan
import sdk_utils as utils


TEST_JOBS = (
    WRITE_DATA_JOB, VERIFY_DATA_JOB, DELETE_DATA_JOB, VERIFY_DELETION_JOB
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False)
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    tmp_dir = tempfile.mkdtemp(prefix='cassandra-test')
    for job in TEST_JOBS:
        jobs.install_job(job, tmp_dir=tmp_dir)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)

    # remove job definitions from metronome
    for job in TEST_JOBS:
        jobs.remove_job(job)


@pytest.mark.sanity
@pytest.mark.smoke
def test_service_health():
    assert shakedown.service_healthy(PACKAGE_NAME)


@pytest.mark.sanity
def test_cleanup_plan_completes():
    cleanup_parameters = {'CASSANDRA_KEYSPACE': 'testspace1'}

    # populate 'testspace1' for test, then delete afterwards:
    with jobs.RunJobContext(
        before_jobs=[WRITE_DATA_JOB, VERIFY_DATA_JOB],
        after_jobs=[DELETE_DATA_JOB, VERIFY_DELETION_JOB]):
        plan.start_plan(PACKAGE_NAME, 'cleanup', parameters=cleanup_parameters)
        plan.wait_for_completed_plan(PACKAGE_NAME, 'cleanup')


@pytest.mark.sanity
def test_repair_plan_completes():
    repair_parameters = {'CASSANDRA_KEYSPACE': 'testspace1'}

    # populate 'testspace1' for test, then delete afterwards:
    with jobs.RunJobContext(
        before_jobs=[WRITE_DATA_JOB, VERIFY_DATA_JOB],
        after_jobs=[DELETE_DATA_JOB, VERIFY_DELETION_JOB]):
        plan.start_plan(PACKAGE_NAME, 'repair', parameters=repair_parameters)
        plan.wait_for_completed_plan(PACKAGE_NAME, 'repair')


# To disable these tests in local runs where you may lack the necessary credentials,
# use e.g. "TEST_TYPES=sanity and not aws and not azure":


@pytest.mark.aws
@pytest.mark.sanity
def test_backup_and_restore_to_s3():
    key_id = os.getenv('AWS_ACCESS_KEY_ID')
    if not key_id:
        assert False, 'AWS credentials are required for this test. Disable test with e.g. TEST_TYPES="sanity and not aws"'
    plan_parameters = {
        'AWS_ACCESS_KEY_ID': key_id,
        'AWS_SECRET_ACCESS_KEY': os.getenv('AWS_SECRET_ACCESS_KEY'),
        'AWS_REGION': os.getenv('AWS_REGION', 'us-west-2'),
        'S3_BUCKET_NAME': os.getenv('AWS_BUCKET_NAME', 'infinity-framework-test'),
        'SNAPSHOT_NAME': str(uuid.uuid1()),
        'CASSANDRA_KEYSPACES': '"testspace1 testspace2"',
    }

    run_backup_and_restore('backup-s3', 'restore-s3', plan_parameters)


@pytest.mark.azure
@pytest.mark.sanity
def test_backup_and_restore_to_azure():
    client_id = os.getenv('AZURE_CLIENT_ID')
    if not client_id:
        assert False, 'Azure credentials are required for this test. Disable test with e.g. TEST_TYPES="sanity and not azure"'
    plan_parameters = {
        'AZURE_CLIENT_ID': client_id,
        'AZURE_CLIENT_SECRET': os.getenv('AZURE_CLIENT_SECRET'),
        'AZURE_TENANT_ID': os.getenv('AZURE_TENANT_ID'),
        'AZURE_STORAGE_ACCOUNT': os.getenv('AZURE_STORAGE_ACCOUNT'),
        'AZURE_STORAGE_KEY': os.getenv('AZURE_STORAGE_KEY'),
        'CONTAINER_NAME': os.getenv('CONTAINER_NAME', 'cassandra-test'),
        'SNAPSHOT_NAME': str(uuid.uuid1()),
        'CASSANDRA_KEYSPACES': '"testspace1 testspace2"',
    }

    run_backup_and_restore('backup-azure', 'restore-azure', plan_parameters)


def run_backup_and_restore(backup_plan, restore_plan, plan_parameters):
    # Write data to Cassandra with a metronome job, then verify it was written
    # Note: Write job will fail if data already exists
    jobs.run_job(WRITE_DATA_JOB)
    jobs.run_job(VERIFY_DATA_JOB)

    # Run backup plan, uploading snapshots and schema to the cloudddd
    plan.start_plan(PACKAGE_NAME, backup_plan, parameters=plan_parameters)
    plan.wait_for_completed_plan(PACKAGE_NAME, backup_plan)

    # Delete all keyspaces and tables with a metronome job
    jobs.run_job(DELETE_DATA_JOB)

    # Verify that the keyspaces and tables were deleted
    jobs.run_job(VERIFY_DELETION_JOB)

    # Run restore plan, retrieving snapshots and schema from S3
    plan.start_plan(PACKAGE_NAME, restore_plan, parameters=plan_parameters)
    plan.wait_for_completed_plan(PACKAGE_NAME, restore_plan)

    # Verify that the data we wrote and then deleted has been restored
    jobs.run_job(VERIFY_DATA_JOB)

    # Delete data in preparation for any other backup tests
    jobs.run_job(DELETE_DATA_JOB)
    jobs.run_job(VERIFY_DELETION_JOB)
