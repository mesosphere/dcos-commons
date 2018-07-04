import os
import uuid

import pytest
import shakedown

import sdk_cmd
import sdk_install
import sdk_jobs
import sdk_plan
import sdk_tasks
import sdk_utils

from security import transport_encryption

from tests import config

pytestmark = [pytest.mark.skipif(sdk_utils.is_open_dcos(),
                                 reason="Feature only supported in DC/OS EE"),
              pytest.mark.skipif(sdk_utils.dcos_version_less_than("1.10"),
                                 reason="TLS tests require DC/OS 1.10+")]


@pytest.fixture(scope='module')
def dcos_ca_bundle():
    """
    Retrieve DC/OS CA bundle and returns the content.
    """
    resp = sdk_cmd.cluster_request('GET', '/ca/dcos-ca.crt')
    cert = resp.content.decode('ascii')
    assert cert is not None
    return cert


@pytest.fixture(scope='module')
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        name = config.SERVICE_NAME
        service_account_info = transport_encryption.setup_service_account(name)

        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(config.SERVICE_NAME,
                                                     service_account_info)


@pytest.fixture(scope='module')
def cassandra_service_tls(service_account):
    sdk_install.uninstall(package_name=config.PACKAGE_NAME, service_name=config.SERVICE_NAME)
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT,
        additional_options={
            "service": {
                "service_account": service_account["name"],
                "service_account_secret": service_account["secret"],
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
            assert False, 'AWS credentials are required for this test. ' \
                          'Disable test with e.g. TEST_TYPES="sanity and not aws"'
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

        # Run restore plan, downloading snapshots and schema from the cloudddd
        sdk_plan.start_plan(config.SERVICE_NAME, 'restore-s3', parameters=plan_parameters)
        sdk_plan.wait_for_completed_plan(config.SERVICE_NAME, 'restore-s3')

    with sdk_jobs.InstallJobContext([
            config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle),
            config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle)]):

        sdk_jobs.run_job(config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle))
        sdk_jobs.run_job(config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle))


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.recovery
def test_tls_recovery(kafka_service_tls, service_account):
    pod_name = "node-0"
    inital_task_id = sdk_tasks.get_task_ids(config.SERVICE_NAME, pod_name)

    cmd_list = [
        "pod", "replace", pod_name,
    ]
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME,
                    " ".join(cmd_list))

    recovery_timeout_s = 25 * 60
    sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME, recovery_timeout_s)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME, recovery_timeout_s)

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, pod_name, inital_task_id)

    # TODO: Add checks for non-updated tasks
