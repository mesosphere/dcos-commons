import os
import uuid
from typing import Any, Dict, Iterator, List

import pytest
import sdk_install
import sdk_jobs
import sdk_cmd
import sdk_marathon
import subprocess
import tempfile
import json
import sdk_security
import sdk_utils

# import json
from tests import config


no_strict_for_azure = pytest.mark.skipif(
    os.environ.get("SECURITY") == "strict",
    reason="backup/restore doesn't work in strict as user needs to be root",
)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security: None) -> Iterator[None]:
    test_jobs: List[Dict[str, Any]] = []
    try:
        test_jobs = config.get_all_jobs(node_address=config.get_foldered_node_address())
        # destroy/reinstall any prior leftover jobs, so that they don't touch the newly installed service:
        for job in test_jobs:
            sdk_jobs.install_job(job)

        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())
        # user=root because Azure CLI needs to run in root...
        # We don't run the Azure tests in strict however, so don't set it then.
        if os.environ.get("SECURITY") == "strict":
            additional_options = {"service": {"name": config.get_foldered_service_name()}}
        else:
            additional_options = {
                "service": {"name": config.get_foldered_service_name(), "user": "root"}
            }

        sdk_install.install(
            config.PACKAGE_NAME,
            config.get_foldered_service_name(),
            config.DEFAULT_TASK_COUNT,
            additional_options=additional_options,
        )

        yield  # let the test session execute
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
def test_backup_and_restore_to_azure() -> None:
    client_id = os.getenv("AZURE_CLIENT_ID")
    if not client_id:
        assert (
            False
        ), 'Azure credentials are required for this test. Disable test with e.g. TEST_TYPES="sanity and not azure"'
    plan_parameters = {
        "CLIENT_ID": client_id,
        "CLIENT_SECRET": os.getenv("AZURE_CLIENT_SECRET"),
        "TENANT_ID": os.getenv("AZURE_TENANT_ID"),
        "AZURE_STORAGE_ACCOUNT": os.getenv("AZURE_STORAGE_ACCOUNT"),
        "AZURE_STORAGE_KEY": os.getenv("AZURE_STORAGE_KEY"),
        "CONTAINER_NAME": os.getenv("CONTAINER_NAME", "cassandra-test"),
        "SNAPSHOT_NAME": str(uuid.uuid1()),
        "CASSANDRA_KEYSPACES": '"testspace1 testspace2"',
    }

    config.run_backup_and_restore(
        config.get_foldered_service_name(),
        "backup-azure",
        "restore-azure",
        plan_parameters,
        config.get_foldered_node_address(),
    )


@pytest.mark.aws
@pytest.mark.sanity
def test_backup_and_restore_to_s3() -> None:
    key_id = os.getenv("AWS_ACCESS_KEY_ID")
    if not key_id:
        assert (
            False
        ), 'AWS credentials are required for this test. Disable test with e.g. TEST_TYPES="sanity and not aws"'
    plan_parameters = {
        "AWS_ACCESS_KEY_ID": key_id,
        "AWS_SECRET_ACCESS_KEY": os.getenv("AWS_SECRET_ACCESS_KEY"),
        "AWS_REGION": os.getenv("AWS_REGION", "us-west-2"),
        "S3_BUCKET_NAME": os.getenv("AWS_BUCKET_NAME", "infinity-framework-test"),
        "SNAPSHOT_NAME": str(uuid.uuid1()),
        "CASSANDRA_KEYSPACES": '"testspace1 testspace2"',
    }

    config.run_backup_and_restore(
        config.get_foldered_service_name(),
        "backup-s3",
        "restore-s3",
        plan_parameters,
        config.get_foldered_node_address(),
    )


@pytest.mark.aws
@pytest.mark.sanity
def test_backup_and_restore_to_s3_compatible_storage() -> None:
    try:
        sdk_cmd.run_cli("package install minio --yes")
        temp_key_id = os.getenv("AWS_ACCESS_KEY_ID")

        if not temp_key_id:
            assert (
                False
            ), 'AWS credentials are required for this test. Disable test with e.g. TEST_TYPES="sanity and not aws"'
        temp_secret_Access_key = os.getenv("AWS_SECRET_ACCESS_KEY")
        is_strict = sdk_utils.is_strict_mode()
        if is_strict:
            sdk_security.create_service_account(
                service_account_name="marathon-lb-sa",
                service_account_secret="marathon-lb/service-account-secret",
            )
            sdk_cmd.run_cli(
                "security org users grant marathon-lb-sa dcos:service:marathon:marathon:services:/ read"
            )
            sdk_cmd.run_cli(
                'security org users grant marathon-lb-sa dcos:service:marathon:marathon:admin:events read --description "Allows access to Marathon events"'
            )
            options = {
                "marathon-lb": {
                    "secret_name": "marathon-lb/service-account-secret",
                    "marathon-uri": "https://marathon.mesos:8443",
                }
            }

            options_file = tempfile.NamedTemporaryFile("w")
            json.dump(options, options_file)
            options_file.flush()
            sdk_cmd.run_cli(
                "package install marathon-lb --yes --options={}".format(options_file.name)
            )

        else:
            sdk_cmd.run_cli("package install marathon-lb --yes")

        sdk_marathon.wait_for_deployment("marathon-lb", 1200, None)
        sdk_marathon.wait_for_deployment("minio", 1200, None)
        host = sdk_marathon.get_scheduler_host("marathon-lb")
        _, public_node_ip, _ = sdk_cmd.agent_ssh(host, "curl -s ifconfig.co")
        minio_endpoint_url = "http://" + public_node_ip + ":9000"
        os.environ["AWS_ACCESS_KEY_ID"] = config.MINIO_AWS_ACCESS_KEY_ID
        os.environ["AWS_SECRET_ACCESS_KEY"] = config.MINIO_AWS_SECRET_ACCESS_KEY
        subprocess.run(
            [
                "aws",
                "s3",
                "mb",
                "s3://" + config.MINIO_BUCKET_NAME,
                "--endpoint",
                minio_endpoint_url,
            ]
        )

        plan_parameters = {
            "AWS_ACCESS_KEY_ID": os.getenv("AWS_ACCESS_KEY_ID"),
            "AWS_SECRET_ACCESS_KEY": os.getenv("AWS_SECRET_ACCESS_KEY"),
            "AWS_REGION": os.getenv("AWS_REGION", "us-west-2"),
            "S3_BUCKET_NAME": config.MINIO_BUCKET_NAME,
            "SNAPSHOT_NAME": str(uuid.uuid1()),
            "CASSANDRA_KEYSPACES": '"testspace1 testspace2"',
            "S3_ENDPOINT_URL": minio_endpoint_url,
        }

        config.run_backup_and_restore(
            config.get_foldered_service_name(),
            "backup-s3",
            "restore-s3",
            plan_parameters,
            config.get_foldered_node_address(),
        )
    finally:
        sdk_install.uninstall("minio", "minio")
        sdk_install.uninstall("marathon-lb", "marathon-lb")
        os.environ["AWS_ACCESS_KEY_ID"] = temp_key_id
        os.environ["AWS_SECRET_ACCESS_KEY"] = temp_secret_Access_key
