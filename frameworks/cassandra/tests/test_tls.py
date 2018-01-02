import os
import uuid

import dcos.http
import pytest
import shakedown

import sdk_cmd
import sdk_install
import sdk_jobs
import sdk_plan
import sdk_security
import sdk_utils
from tests import config


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
    # This name should be same as SERVICE_NAME as it determines scheduler DCOS_LABEL value.
    name = config.SERVICE_NAME
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
    sdk_install.uninstall(package_name=config.PACKAGE_NAME, service_name=config.SERVICE_NAME)
    sdk_install.install(
        config.PACKAGE_NAME,
        service_account,
        config.DEFAULT_TASK_COUNT,
        additional_options={
            "service": {
                "service_account_secret": service_account,
                "service_account": service_account,
                "security": {
                    "transport_encryption": {
                        "enabled": True
                    }
                }
            }
        }
    )

    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # Wait for service health check to pass
    shakedown.service_healthy(config.SERVICE_NAME)

    yield

    sdk_install.uninstall(package_name=config.PACKAGE_NAME, service_name=config.SERVICE_NAME)


@pytest.mark.aws
@pytest.mark.sanity
@pytest.mark.tls
@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
def test_tls_connection(cassandra_service_tls, dcos_ca_bundle):
    """
    Tests writing, reading and deleting data over a secure TLS connection.
    """
    with sdk_jobs.InstallJobContext([
            config.get_write_data_job(dcos_ca_bundle=dcos_ca_bundle),
            config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle),
            config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle)]):

        sdk_jobs.run_job(config.get_write_data_job(dcos_ca_bundle=dcos_ca_bundle))
        sdk_jobs.run_job(config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle))

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
        sdk_plan.start_plan(config.SERVICE_NAME, 'backup-s3', parameters=plan_parameters)
        sdk_plan.wait_for_completed_plan(config.SERVICE_NAME, 'backup-s3')

        sdk_jobs.run_job(config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle))

        # Run backup plan, uploading snapshots and schema to the cloudddd
        sdk_plan.start_plan(config.SERVICE_NAME, 'restore-s3', parameters=plan_parameters)
        sdk_plan.wait_for_completed_plan(config.SERVICE_NAME, 'restore-s3')

    with sdk_jobs.InstallJobContext([
            config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle),
            config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle)]):

        sdk_jobs.run_job(config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle))
        sdk_jobs.run_job(config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle))
