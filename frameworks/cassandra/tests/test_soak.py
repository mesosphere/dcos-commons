import json
import os
import uuid

import dcos
import pytest
import shakedown

from tests.config import (
    DEFAULT_TASK_COUNT,
    DELETE_DATA_JOB,
    PACKAGE_NAME,
    TEST_JOBS,
    VERIFY_DATA_JOB,
    VERIFY_DELETION_JOB,
    WRITE_DATA_JOB,
    DataContext,
    EnvironmentContext,
    JobContext,
    get_jobs_folder,
    install_cassandra_jobs,
    install_job,
    launch_and_verify_job,
    remove_cassandra_jobs,
    remove_job,
)
from tests.test_backup import run_backup_and_restore
import sdk_api as api
import sdk_plan as plan
import sdk_spin as spin
import sdk_test_upgrade
import sdk_utils as utils


def get_dcos_cassandra_plan(service_name):
    utils.out('Waiting for {} plan to complete...'.format(service_name))

    def fn():
        return api.get(service_name, '/v1/plan')
    return spin.time_wait_return(fn)


@pytest.mark.soak_backup
def test_backup_and_restore():
    plan_parameters = {
        'S3_BUCKET_NAME': os.getenv(
            'AWS_BUCKET_NAME', 'infinity-framework-test'
        ),
        'AWS_ACCESS_KEY_ID': os.getenv('AWS_ACCESS_KEY_ID'),
        'AWS_SECRET_ACCESS_KEY': os.getenv('AWS_SECRET_ACCESS_KEY'),
        'AWS_REGION': os.getenv('AWS_REGION', 'us-west-2'),
        'SNAPSHOT_NAME': str(uuid.uuid1()),
        'CASSANDRA_KEYSPACES': '"testspace1 testspace2"',
    }

    with JobContext(TEST_JOBS):
        run_backup_and_restore('backup-s3', 'restore-s3', plan_parameters)


@pytest.mark.soak_upgrade
def test_soak_upgrade_downgrade():
    """Install the Cassandra Universe package and attempt upgrade to master.

    Assumes that the install options file is placed in the repo root."""
    with open('cassandra.json') as options_file:
        install_options = json.load(options_file)

    sdk_test_upgrade.soak_upgrade_downgrade(
        PACKAGE_NAME, DEFAULT_TASK_COUNT, install_options
    )


@pytest.mark.soak_migration
def test_cassandra_migration():
    backup_service_name = os.getenv('CASSANDRA_BACKUP_CLUSTER_NAME')
    restore_service_name = os.getenv('CASSANDRA_RESTORE_CLUSTER_NAME')

    env = EnvironmentContext(
        CASSANDRA_NODE_ADDRESS=os.getenv(
            'BACKUP_NODE_ADDRESS', 'node-0.cassandra.autoip.dcos.thisdcos.directory'
        ),
        CASSANDRA_NODE_PORT=os.getenv('BACKUP_NODE_PORT', '9042')
    )
    plan_parameters = {
        'S3_BUCKET_NAME': os.getenv(
            'AWS_BUCKET_NAME', 'infinity-framework-test'
        ),
        'AWS_ACCESS_KEY_ID': os.getenv('AWS_ACCESS_KEY_ID'),
        'AWS_SECRET_ACCESS_KEY': os.getenv('AWS_SECRET_ACCESS_KEY'),
        'AWS_REGION': os.getenv('AWS_REGION', 'us-west-2'),
        'SNAPSHOT_NAME': str(uuid.uuid1()),
        'CASSANDRA_KEYSPACES': '"testspace1 testspace2"',
    }

    data_context = DataContext(
        init_jobs=[WRITE_DATA_JOB, VERIFY_DATA_JOB],
        cleanup_jobs=[DELETE_DATA_JOB, VERIFY_DELETION_JOB]
    )
    # Install and run the write/delete data jobs against backup cluster,
    # running dcos-cassandra-service
    with env, JobContext(TEST_JOBS), data_context:
        # Back this cluster up to S3
        backup_parameters = {
            'backup_name': plan_parameters['SNAPSHOT_NAME'],
            's3_access_key': plan_parameters['AWS_ACCESS_KEY_ID'],
            's3_secret_key': plan_parameters['AWS_SECRET_ACCESS_KEY'],
            'external_location': 's3://{}'.format(plan_parameters['S3_BUCKET_NAME']),
        }
        dcos.http.put(
            '{}v1/backup/start'.format(
                shakedown.dcos_service_url(backup_service_name)
            ),
            json=backup_parameters
        )
        spin.time_wait_noisy(
            lambda: get_dcos_cassandra_plan(
                backup_service_name
            ).json()['status'] == 'COMPLETE'
        )

    env = EnvironmentContext(
        CASSANDRA_NODE_ADDRESS=os.getenv(
            'RESTORE_NODE_ADDRESS', 'node-0-server.sdk-cassandra.autoip.dcos.thisdcos.directory'
        ),
        CASSANDRA_NODE_PORT=os.getenv('RESTORE_NODE_PORT', '9052')
    )

    data_context = DataContext(
        cleanup_jobs=[VERIFY_DATA_JOB, DELETE_DATA_JOB, VERIFY_DELETION_JOB]
    )
    with env, JobContext(TEST_JOBS), data_context:
        plan.start_plan(
            restore_service_name, 'restore-s3', parameters=plan_parameters
        )
        spin.time_wait_noisy(
            lambda: (
                plan.get_plan(
                    restore_service_name, 'restore-s3'
                ).json()['status'] == 'COMPLETE'
            )
        )
