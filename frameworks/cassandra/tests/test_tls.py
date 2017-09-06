import json
import os
import textwrap
import uuid
from typing import List

import dcos.http
import pytest
import requests
import shakedown

import sdk_cmd
import sdk_install
import sdk_jobs
import sdk_networks
import sdk_plan
import sdk_security
import sdk_utils


from tests.config import (
    DEFAULT_NODE_ADDRESS,
    DEFAULT_NODE_PORT,
    DEFAULT_TASK_COUNT,
    PACKAGE_NAME,
    _get_test_job
)


@pytest.fixture(scope='module')
def dcos_ca_bundle():
    """
    Retrieve DC/OS CA bundle and returns the content.
    """
    url = shakedown.dcos_url_path('ca/dcos-ca.crt')
    resp = dcos.http.request('get', url)
    cert = resp.content.decode('ascii')
    assert cert is not None
    return cert


@pytest.fixture(scope='module')
def service_account():
    """
    Creates service account with `hello-world` name and yields the name.
    """
    name = PACKAGE_NAME
    sdk_security.create_service_account(
        service_account_name=name, service_account_secret=name)
     # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
    sdk_cmd.run_cli(
        "security org groups add_user superusers {name}".format(name=name))
    yield name
    sdk_security.delete_service_account(
        service_account_name=name, service_account_secret=name)


@pytest.fixture(scope='module')
def cassandra_service_tls(service_account):
    sdk_install.install(
        PACKAGE_NAME,
        DEFAULT_TASK_COUNT,
        service_name=service_account,
        additional_options={
            "service": {
                "service_account_secret": service_account,
                "service_account": service_account,
                "tls": True,
                "tls_allow_plaintext": False,
            }
        }
    )

    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)

    # Wait for service health check to pass
    shakedown.service_healthy(PACKAGE_NAME)

    yield

    sdk_install.uninstall(PACKAGE_NAME)


def get_cqlsh_tls_rc_config(
        certfile='/mnt/mesos/sandbox/ca-bundle.crt',
        hostname=DEFAULT_NODE_ADDRESS,
        port=DEFAULT_NODE_PORT
    ):
    """
    Returns a content of `cqlshrc` configuration file with provided hostname,
    port and certfile location. The configuration can be used for connecting
    to cassandra over a TLS connection.
    """
    return textwrap.dedent("""
        [cql]
        ; Substitute for the version of Cassandra you are connecting to.
        version = 3.4.0

        [connection]
        hostname = {hostname}
        port = {port}
        factory = cqlshlib.ssl.ssl_transport_factory

        [ssl]
        certfile = {certfile}
        ; Note: If validate = true then the certificate name must match the machine's hostname
        validate = true
        ; If using client authentication (require_client_auth = true in cassandra.yaml) you'll also need to point to your uesrkey and usercert.
        ; SSL client authentication is only supported via cqlsh on C* 2.1 and greater.
        ; This is disabled by default on all Instaclustr-managed clusters.
        ; userkey = /path/to/userkey.pem
        ; usercert = /path/to/usercert.pem
        """.format(
            hostname=hostname,
            port=port,
            certfile=certfile
        ))


def get_write_data_job(dcos_ca_bundle: str):
    query = ' '.join([
        "CREATE KEYSPACE testspace1 WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };",
        "USE testspace1;",
        "CREATE TABLE testtable1 (key varchar, value varchar, PRIMARY KEY(key));",
        "INSERT INTO testspace1.testtable1(key, value) VALUES('testkey1', 'testvalue1');",

        "CREATE KEYSPACE testspace2 WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };",
        "USE testspace2;",
        "CREATE TABLE testtable2 (key varchar, value varchar, PRIMARY KEY(key));",
        "INSERT INTO testspace2.testtable2(key, value) VALUES('testkey2', 'testvalue2');",
    ])
    return _get_cqlsh_job_over_tls(
        'write-data',
        [_get_cqlsh_for_query(query)],
        dcos_ca_bundle=dcos_ca_bundle,
    )


def get_verify_data_job(dcos_ca_bundle: str):
    commands = [
        '{command} | grep testkey1'.format(command=_get_cqlsh_for_query('SELECT * FROM testspace1.testtable1;')),
        '{command} | grep testkey2'.format(command=_get_cqlsh_for_query('SELECT * FROM testspace2.testtable2;')),
    ]
    return _get_cqlsh_job_over_tls(
        'verify-data', commands, dcos_ca_bundle=dcos_ca_bundle)


def get_delete_data_job(dcos_ca_bundle: str):
    query = ' '.join([
        'TRUNCATE testspace1.testtable1;',
        'TRUNCATE testspace2.testtable2;',
        'DROP KEYSPACE testspace1;',
        'DROP KEYSPACE testspace2;'])
    return _get_cqlsh_job_over_tls(
        'delete-data',
        [_get_cqlsh_for_query(query)],
        dcos_ca_bundle=dcos_ca_bundle,
    )


def get_verify_deletion_job(dcos_ca_bundle: str):
    commands = [
        '{command} | grep "0 rows"'.format(
            command=_get_cqlsh_for_query('SELECT * FROM system_schema.tables WHERE keyspace_name=\'testspace1\';')),
        '{command} | grep "0 rows'.format(
            command=_get_cqlsh_for_query('SELECT * FROM system_schema.tables WHERE keyspace_name=\'testspace2\';')),
    ]
    return _get_cqlsh_job_over_tls(
        'delete-data', commands, dcos_ca_bundle=dcos_ca_bundle)


def _get_cqlsh_job_over_tls(
        name: str,
        commands: List[str],
        dcos_ca_bundle: str=None):
    """
    Creates a DC/OS job with `cqlsh` utility that will be ready to run commands
    over a TLS connection.
    """
    commands_with_tls_prepare = [
        'echo -n "$CQLSHRC_FILE" > $MESOS_SANDBOX/cqlshrc',
        'echo -n "$CA_BUNDLE" > $MESOS_SANDBOX/ca-bundle.crt',
    ]
    commands_with_tls_prepare.extend(commands)
    cmd = ' && '.join(commands_with_tls_prepare)

    job = _get_test_job(name=name, cmd=cmd)
    job['run']['env'] = {
        'CQLSHRC_FILE': get_cqlsh_tls_rc_config(),
        'CA_BUNDLE': dcos_ca_bundle,
    }
    return job


def _get_cqlsh_for_query(query: str):
    """
    Creates a `cqlsh` command for given query that will be executed over a TLS
    connection.
    """
    return 'cqlsh --cqlshrc="$MESOS_SANDBOX/cqlshrc" --ssl -e "{query}"'.format(
        query=query)


@pytest.mark.aws
@pytest.mark.sanity
@pytest.mark.tls
@sdk_utils.dcos_1_10_or_higher
@sdk_utils.dcos_ee_only
def test_tls_connection(cassandra_service_tls, dcos_ca_bundle):
    """
    Tests writing, reading and deleting data over a secure TLS connection.
    """
    with sdk_jobs.InstallJobContext([
            get_write_data_job(dcos_ca_bundle),
            get_verify_data_job(dcos_ca_bundle),
            get_delete_data_job(dcos_ca_bundle)]):

        sdk_jobs.run_job(get_write_data_job(dcos_ca_bundle))
        sdk_jobs.run_job(get_verify_data_job(dcos_ca_bundle))

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

        # Run backup plan, uploading snapshots and schema to the cloudddd
        sdk_plan.start_plan(PACKAGE_NAME, 'backup-s3', parameters=plan_parameters)
        sdk_plan.wait_for_completed_plan(PACKAGE_NAME, 'backup-s3')

        sdk_jobs.run_job(get_delete_data_job(dcos_ca_bundle))

        # Run backup plan, uploading snapshots and schema to the cloudddd
        sdk_plan.start_plan(PACKAGE_NAME, 'restore-s3', parameters=plan_parameters)
        sdk_plan.wait_for_completed_plan(PACKAGE_NAME, 'restore-s3')

    with sdk_jobs.InstallJobContext([
            get_verify_data_job(dcos_ca_bundle),
            get_delete_data_job(dcos_ca_bundle)]):

        sdk_jobs.run_job(get_verify_data_job(dcos_ca_bundle))
        sdk_jobs.run_job(get_delete_data_job(dcos_ca_bundle))
