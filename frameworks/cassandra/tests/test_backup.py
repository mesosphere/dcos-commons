import os
import sys
import uuid

import pytest

from tests.config import *
import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan
import sdk_spin as spin
import sdk_tasks as tasks
import sdk_utils as utils


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    install_cassandra_jobs()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False)


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)

    remove_cassandra_jobs()


@pytest.mark.sanity
def test_backup_and_restore_flow():
    run_backup_and_restore()


def run_backup_and_restore():
    backup_parameters = {
        'S3_BUCKET_NAME': os.getenv(
            'AWS_BUCKET_NAME', 'infinity-framework-test'
        ),
        'AWS_ACCESS_KEY_ID': os.getenv('AWS_ACCESS_KEY_ID'),
        'AWS_SECRET_ACCESS_KEY': os.getenv('AWS_SECRET_ACCESS_KEY'),
        'AWS_REGION': os.getenv('AWS_REGION', 'us-west-2'),
        'SNAPSHOT_NAME': str(uuid.uuid1()),
        'CASSANDRA_KEYSPACES': '"testspace1 testspace2"',
    }

    # Write data to Cassandra with a metronome job
    launch_and_verify_job(WRITE_DATA_JOB)

    # Verify that the data was written
    launch_and_verify_job(VERIFY_DATA_JOB)

    # Run backup plan, uploading snapshots and schema to S3 
    plan.start_plan(PACKAGE_NAME, 'backup-s3', parameters=backup_parameters)
    spin.time_wait_noisy(
        lambda: (
            plan.get_plan(PACKAGE_NAME, 'backup-s3').json()['status'] ==
            'COMPLETE'
        )
    )

    # Delete all keyspaces and tables with a metronome job
    launch_and_verify_job(DELETE_DATA_JOB)

    # Verify that the keyspaces and tables were deleted
    launch_and_verify_job(VERIFY_DELETION_JOB)

    # Run restore plan, retrieving snapshots and schema from S3
    plan.start_plan(PACKAGE_NAME, 'restore-s3', parameters=backup_parameters)
    spin.time_wait_noisy(
        lambda: (
            plan.get_plan(PACKAGE_NAME, 'restore-s3').json()['status'] ==
            'COMPLETE'
        )
    )

    # Verify that the data we wrote and then deleted has been restored
    launch_and_verify_job(VERIFY_DATA_JOB, expected_successes=2)
