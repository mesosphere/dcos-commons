""" Utilties specific to Cassandra tests """

import os
import logging
import traceback

import sdk_hosts
import sdk_jobs
import sdk_plan
import sdk_utils

log = logging.getLogger(__name__)

PACKAGE_NAME = 'beta-cassandra'

SERVICE_NAME = os.environ.get('SOAK_SERVICE_NAME') or 'cassandra'

DEFAULT_TASK_COUNT = 3
DEFAULT_CASSANDRA_TIMEOUT = 600
# Soak artifact scripts may override the service name to test

DEFAULT_NODE_ADDRESS = os.getenv('CASSANDRA_NODE_ADDRESS', sdk_hosts.autoip_host(SERVICE_NAME, 'node-0-server'))
DEFAULT_NODE_PORT = os.getenv('CASSANDRA_NODE_PORT', '9042')


def _get_test_job(name, cmd, restart_policy='ON_FAILURE'):
    return {
        'description': '{} with restart policy {}'.format(name, restart_policy),
        'id': 'test.cassandra.' + name,
        'run': {
            'cmd': cmd,
            'docker': {'image': 'cassandra:3.0.13'},
            'cpus': 1,
            'mem': 512,
            'user': 'nobody',
            'restart': {'policy': restart_policy}
        }
    }


def get_foldered_service_name():
    return sdk_utils.get_foldered_name(SERVICE_NAME)


def get_foldered_node_address():
    return sdk_hosts.autoip_host(get_foldered_service_name(), 'node-0-server')


def get_delete_data_job(node_address=DEFAULT_NODE_ADDRESS, node_port=DEFAULT_NODE_PORT):
    cql = ' '.join([
        'TRUNCATE testspace1.testtable1;',
        'TRUNCATE testspace2.testtable2;',
        'DROP KEYSPACE testspace1;',
        'DROP KEYSPACE testspace2;'])
    return _get_test_job(
        'delete-data',
        'cqlsh --cqlversion=3.4.0 -e "{}" {} {}'.format(cql, node_address, node_port))


def get_verify_data_job(node_address=DEFAULT_NODE_ADDRESS, node_port=DEFAULT_NODE_PORT):
    cmd = ' && '.join([
        'cqlsh --cqlversion=3.4.0 -e "SELECT * FROM testspace1.testtable1;" {address} {port} | grep testkey1',
        'cqlsh --cqlversion=3.4.0 -e "SELECT * FROM testspace2.testtable2;" {address} {port} | grep testkey2'])
    return _get_test_job(
        'verify-data',
        cmd.format(address=node_address, port=node_port))


def get_verify_deletion_job(node_address=DEFAULT_NODE_ADDRESS, node_port=DEFAULT_NODE_PORT):
    cmd = ' && '.join([
        'cqlsh --cqlversion=3.4.0 -e "SELECT * FROM system_schema.tables WHERE keyspace_name=\'testspace1\';" {address} {port} | grep "0 rows"',
        'cqlsh --cqlversion=3.4.0 -e "SELECT * FROM system_schema.tables WHERE keyspace_name=\'testspace2\';" {address} {port} | grep "0 rows"'])
    return _get_test_job(
        'verify-deletion',
        cmd.format(address=node_address, port=node_port))


def get_write_data_job(node_address=DEFAULT_NODE_ADDRESS, node_port=DEFAULT_NODE_PORT):
    cql = ' '.join([
        "CREATE KEYSPACE testspace1 WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };",
        "USE testspace1;",
        "CREATE TABLE testtable1 (key varchar, value varchar, PRIMARY KEY(key));",
        "INSERT INTO testspace1.testtable1(key, value) VALUES('testkey1', 'testvalue1');",

        "CREATE KEYSPACE testspace2 WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };",
        "USE testspace2;",
        "CREATE TABLE testtable2 (key varchar, value varchar, PRIMARY KEY(key));",
        "INSERT INTO testspace2.testtable2(key, value) VALUES('testkey2', 'testvalue2');"])
    return _get_test_job(
        'write-data',
        'cqlsh --cqlversion=3.4.0 -e "{}" {} {}'.format(cql, node_address, node_port))


def get_all_jobs(node_address=DEFAULT_NODE_ADDRESS, node_port=DEFAULT_NODE_PORT):
    write_data_job = get_write_data_job(node_address)
    verify_data_job = get_verify_data_job(node_address)
    delete_data_job = get_delete_data_job(node_address)
    verify_deletion_job = get_verify_deletion_job(node_address)
    return [write_data_job, verify_data_job, delete_data_job, verify_deletion_job]


def run_backup_and_restore(
        service_name,
        backup_plan,
        restore_plan,
        plan_parameters,
        job_node_address=DEFAULT_NODE_ADDRESS):
    write_data_job = get_write_data_job(node_address=job_node_address)
    verify_data_job = get_verify_data_job(node_address=job_node_address)
    delete_data_job = get_delete_data_job(node_address=job_node_address)
    verify_deletion_job = get_verify_deletion_job(node_address=job_node_address)


    # Ensure the keyspaces we will use aren't present.
    try:
        jobs.run_job(delete_data_job)
    except:
        log.info("Error during delete (normal if no stale data).")
        tb = traceback.format_exc()
        log.info(tb)

    # Write data to Cassandra with a metronome job, then verify it was written
    # Note: Write job will fail if data already exists
    sdk_jobs.run_job(write_data_job)
    sdk_jobs.run_job(verify_data_job)

    # Run backup plan, uploading snapshots and schema to the cloudddd
    sdk_plan.start_plan(service_name, backup_plan, parameters=plan_parameters)
    sdk_plan.wait_for_completed_plan(service_name, backup_plan)

    # Delete all keyspaces and tables with a metronome job
    sdk_jobs.run_job(delete_data_job)

    # Verify that the keyspaces and tables were deleted
    sdk_jobs.run_job(verify_deletion_job)

    # Run restore plan, retrieving snapshots and schema from S3
    sdk_plan.start_plan(service_name, restore_plan, parameters=plan_parameters)
    sdk_plan.wait_for_completed_plan(service_name, restore_plan)

    # Verify that the data we wrote and then deleted has been restored
    sdk_jobs.run_job(verify_data_job)

    # Delete data in preparation for any other backup tests
    sdk_jobs.run_job(delete_data_job)
    sdk_jobs.run_job(verify_deletion_job)
