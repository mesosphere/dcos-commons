import os
import tempfile
import uuid

import pytest
import sdk_install
import sdk_jobs
from tests import config


no_strict_for_azure = pytest.mark.skipif(os.environ.get("SECURITY") == "strict",
        reason="backup/restore doesn't work in strict as user needs to be root")

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    test_jobs = []
    try:
        test_jobs = config.get_all_jobs(node_address=config.get_foldered_node_address())
        # destroy any leftover jobs first, so that they don't touch the newly installed service:
        for job in test_jobs:
            sdk_jobs.remove_job(job)

        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())
        # user=root because Azure CLI needs to run in root...
        # We don't run the Azure tests in strict however, so don't set it then.
        if os.environ.get("SECURITY") == "strict":
            additional_options={"service": { "name": config.get_foldered_service_name() } }
        else:
            additional_options={"service": { "name": config.get_foldered_service_name(), "user": "root" } }

        sdk_install.install(
            config.PACKAGE_NAME,
            config.get_foldered_service_name(),
            config.DEFAULT_TASK_COUNT,
            additional_options=additional_options)

        tmp_dir = tempfile.mkdtemp(prefix='cassandra-test')
        for job in test_jobs:
            sdk_jobs.install_job(job, tmp_dir=tmp_dir)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())

        # remove job definitions from metronome
        for job in test_jobs:
            sdk_jobs.remove_job(job)

# To disable these tests in local runs where you may lack the necessary credentials,
# use e.g. "TEST_TYPES=sanity and not aws and not azure":

@pytest.mark.azure
@no_strict_for_azure
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

    config.run_backup_and_restore(
        config.get_foldered_service_name(),
        'backup-azure',
        'restore-azure',
        plan_parameters,
        config.get_foldered_node_address())


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

    config.run_backup_and_restore(
        config.get_foldered_service_name(),
        'backup-s3',
        'restore-s3',
        plan_parameters,
        config.get_foldered_node_address())
