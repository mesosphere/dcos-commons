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


WRITE_DATA_JOB = 'write-data'
VERIFY_DATA_JOB = 'verify-data'
DELETE_DATA_JOB = 'delete-data'
VERIFY_DELETION_JOB = 'verify-deletion'
TEST_JOBS = (
    WRITE_DATA_JOB, VERIFY_DATA_JOB, DELETE_DATA_JOB, VERIFY_DELETION_JOB
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False)

    jobs_folder = os.path.join(
        os.path.dirname(os.path.realpath(__file__)), 'jobs'
    )
    for job in TEST_JOBS:
        cmd.run_cli('job add {}'.format(
            os.path.join(jobs_folder, '{}.json'.format(job))
        ))


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)

    for job in TEST_JOBS:
        cmd.run_cli('job remove {}'.format(qualified_job_name(job)))


def qualified_job_name(job_name):
    return 'test.cassandra.{}'.format(job_name)


def launch_and_verify_job(job_name, expected_successes=1):
    cmd.run_cli('job run {}'.format(qualified_job_name(job_name)))

    spin.time_wait_noisy(lambda: (
        'Successful runs: {}'.format(expected_successes) in
        cmd.run_cli('job history {}'.format(qualified_job_name(job_name)))
    ))


def test_backup_and_restore_flow():
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
    plan.start_plan(PACKAGE_NAME, 'backup-aws', parameters=backup_parameters)
    spin.time_wait_noisy(
        lambda: (
            plan.get_plan(PACKAGE_NAME, 'backup-aws').json()['status'] ==
            'COMPLETE'
        )
    )

    # Delete all keyspaces and tables with a metronome job
    launch_and_verify_job(DELETE_DATA_JOB)

    # Verify that the keyspaces and tables were deleted
    launch_and_verify_job(VERIFY_DELETION_JOB)

    # Run restore plan, retrieving snapshots and schema from S3
    plan.start_plan(PACKAGE_NAME, 'restore-aws', parameters=backup_parameters)
    spin.time_wait_noisy(
        lambda: (
            plan.get_plan(PACKAGE_NAME, 'restore-aws').json()['status'] ==
            'COMPLETE'
        )
    )

    # Verify that the data we wrote and then deleted has been restored
    launch_and_verify_job(VERIFY_DATA_JOB, expected_successes=2)
