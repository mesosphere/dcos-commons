import json
import os
import pytest
import uuid
from typing import Any, Dict, Iterable

import sdk_cmd
import sdk_install
import sdk_jobs
import sdk_plan
import sdk_recovery
import sdk_utils

from security import transport_encryption

from tests import config

pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"), reason="TLS tests require DC/OS 1.10+"
    ),
]


@pytest.fixture(scope="module")
def dcos_ca_bundle() -> bytes:
    """
    Retrieve DC/OS CA bundle and returns the content.
    """
    return transport_encryption.fetch_dcos_ca_bundle_contents().decode("ascii")


@pytest.fixture(scope="module")
def service_account(configure_security: None) -> Iterable[Dict[str, Any]]:
    """
    Sets up a service account for use with TLS.
    """
    try:
        name = config.SERVICE_NAME
        service_account_info = transport_encryption.setup_service_account(name)

        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(config.SERVICE_NAME, service_account_info)


@pytest.fixture(scope="module")
def cassandra_service(service_account: Dict[str, Any]) -> Iterable:
    service_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {"transport_encryption": {"enabled": True}},
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            service_name=config.SERVICE_NAME,
            expected_running_tasks=config.DEFAULT_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
        )

        yield {**service_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.aws
@pytest.mark.sanity
@pytest.mark.tls
def test_tls_connection(
    cassandra_service: Dict[str, Any],
    dcos_ca_bundle: bytes,
) -> None:
    """
    Tests writing, reading and deleting data over a secure TLS connection.
    """
    with sdk_jobs.InstallJobContext(
        [
            config.get_write_data_job(dcos_ca_bundle=dcos_ca_bundle),
            config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle),
            config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle),
        ]
    ):

        sdk_jobs.run_job(config.get_write_data_job(dcos_ca_bundle=dcos_ca_bundle))
        sdk_jobs.run_job(config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle))

        key_id = os.getenv("AWS_ACCESS_KEY_ID")
        if not key_id:
            assert (
                False
            ), "AWS credentials are required for this test. " 'Disable test with e.g. TEST_TYPES="sanity and not aws"'
        plan_parameters = {
            "AWS_ACCESS_KEY_ID": key_id,
            "AWS_SECRET_ACCESS_KEY": os.getenv("AWS_SECRET_ACCESS_KEY"),
            "AWS_REGION": os.getenv("AWS_REGION", "us-west-2"),
            "S3_BUCKET_NAME": os.getenv("AWS_BUCKET_NAME", "infinity-framework-test"),
            "SNAPSHOT_NAME": str(uuid.uuid1()),
            "CASSANDRA_KEYSPACES": '"testspace1 testspace2"',
        }

        # Run backup plan, uploading snapshots and schema to the cloudddd
        sdk_plan.start_plan(config.SERVICE_NAME, "backup-s3", parameters=plan_parameters)
        sdk_plan.wait_for_completed_plan(config.SERVICE_NAME, "backup-s3")

        sdk_jobs.run_job(config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle))

        # Run restore plan, downloading snapshots and schema from the cloudddd
        sdk_plan.start_plan(config.SERVICE_NAME, "restore-s3", parameters=plan_parameters)
        sdk_plan.wait_for_completed_plan(config.SERVICE_NAME, "restore-s3")

    with sdk_jobs.InstallJobContext(
        [
            config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle),
            config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle),
        ]
    ):

        sdk_jobs.run_job(config.get_verify_data_job(dcos_ca_bundle=dcos_ca_bundle))
        sdk_jobs.run_job(config.get_delete_data_job(dcos_ca_bundle=dcos_ca_bundle))


@pytest.mark.tls
@pytest.mark.sanity
def test_tls_recovery(
    cassandra_service: Dict[str, Any],
    service_account: Dict[str, Any],
) -> None:
    _, stdout, _ = sdk_cmd.svc_cli(
        cassandra_service["package_name"],
        cassandra_service["service"]["name"],
        "pod list",
    )

    pod_list = json.loads(stdout)
    for pod in pod_list:
        sdk_recovery.check_permanent_recovery(
            cassandra_service["package_name"],
            cassandra_service["service"]["name"],
            pod,
            recovery_timeout_s=25 * 60,
            pods_with_updated_tasks=pod_list,
        )
