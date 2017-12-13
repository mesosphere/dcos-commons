import json
import os
import uuid

import dcos
import pytest
import sdk_hosts
import sdk_jobs
import sdk_plan
import sdk_upgrade
import shakedown
from tests import config


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

    with sdk_jobs.InstallJobContext([
            config.get_write_data_job(),
            config.get_verify_data_job(),
            config.get_delete_data_job(),
            config.get_verify_deletion_job()]):
        config.run_backup_and_restore(
            config.SERVICE_NAME,
            'backup-s3',
            'restore-s3',
            plan_parameters)


@pytest.mark.soak_upgrade
def test_soak_upgrade_downgrade():
    """Install the Cassandra Universe package and attempt upgrade to master.

    Assumes that the install options file is placed in the repo root."""
    with open('cassandra.json') as options_file:
        install_options = json.load(options_file)
    sdk_upgrade.soak_upgrade_downgrade(
        config.PACKAGE_NAME,
        install_options["service"]["name"],
        config.DEFAULT_TASK_COUNT,
        additional_options=install_options)


@pytest.mark.soak_migration
def test_cassandra_migration():
    backup_service_name = os.getenv('CASSANDRA_BACKUP_CLUSTER_NAME')
    restore_service_name = os.getenv('CASSANDRA_RESTORE_CLUSTER_NAME')

    backup_node_address = os.getenv('BACKUP_NODE_ADDRESS', config.DEFAULT_NODE_ADDRESS)
    backup_node_port = os.getenv('BACKUP_NODE_PORT', config.DEFAULT_NODE_PORT)

    backup_write_data_job = config.get_write_data_job(backup_node_address, backup_node_port)
    backup_verify_data_job = config.get_verify_data_job(backup_node_address, backup_node_port)
    backup_delete_data_job = config.get_delete_data_job(backup_node_address, backup_node_port)
    backup_verify_deletion_job = config.get_verify_deletion_job(backup_node_address, backup_node_port)

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

    backup_install_job_context = sdk_jobs.InstallJobContext(
        [backup_write_data_job, backup_verify_data_job,
         backup_delete_data_job, backup_verify_deletion_job])
    backup_run_job_context = sdk_jobs.RunJobContext(
        before_jobs=[backup_write_data_job, backup_verify_data_job],
        after_jobs=[backup_delete_data_job, backup_verify_deletion_job])
    # Install and run the write/delete data jobs against backup cluster,
    # running dcos-cassandra-service
    with backup_install_job_context, backup_run_job_context:
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
        sdk_plan.wait_for_completed_deployment(backup_service_name)

    # Restore data to second instance:
    restore_node_address = os.getenv(
        'RESTORE_NODE_ADDRESS', sdk_hosts.autoip_host('sdk-cassandra', 'node-0-server'))
    restore_node_port = os.getenv('RESTORE_NODE_PORT', '9052')

    restore_write_data_job = config.get_write_data_job(restore_node_address, restore_node_port)
    restore_verify_data_job = config.get_verify_data_job(restore_node_address, restore_node_port)
    restore_delete_data_job = config.get_delete_data_job(restore_node_address, restore_node_port)
    restore_verify_deletion_job = config.get_verify_deletion_job(restore_node_address, restore_node_port)

    restore_install_job_context = sdk_jobs.InstallJobContext(
        [restore_write_data_job, restore_verify_data_job,
         restore_delete_data_job, restore_verify_deletion_job]
    )
    restore_run_job_context = sdk_jobs.RunJobContext(
        after_jobs=[restore_verify_data_job, restore_delete_data_job, restore_verify_deletion_job]
    )
    with restore_install_job_context, restore_run_job_context:
        sdk_plan.start_plan(
            restore_service_name, 'restore-s3', parameters=plan_parameters
        )
        sdk_plan.wait_for_completed_plan(restore_service_name, 'restore-s3')
