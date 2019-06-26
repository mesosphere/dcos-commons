import os
import uuid
import retrying
from typing import Any, Dict, Iterator, List
import logging
import pytest
import sdk_jobs
import sdk_cmd
import sdk_install
import sdk_tasks
import sdk_utils
from tests import config

log = logging.getLogger(__name__)


no_strict_for_azure = pytest.mark.skipif(
    os.environ.get("SECURITY") == "strict",
    reason="backup/restore doesn't work in strict as user needs to be root",
)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security: None) -> Iterator[None]:
    test_jobs: List[Dict[str, Any]] = []
    try:
        test_jobs = config.get_all_jobs(auth=True)
        # destroy/reinstall any prior leftover jobs, so that they don't touch the newly installed service:
        for job in test_jobs:
            sdk_jobs.install_job(job)

        create_secret(
            secret_value=config.SECRET_VALUE,
            secret_path=config.PACKAGE_NAME + "/" + config.SECRET_VALUE,
        )
        service_options = {
            "service": {
                "name": config.SERVICE_NAME,
                "security": {
                    "authentication": {
                        "enabled": True,
                        "superuser": {"password_secret_path": "cassandra/password"},
                    },
                    "authorization": {"enabled": True},
                },
            }
        }

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=service_options,
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        delete_secret(secret=config.PACKAGE_NAME + "/" + config.SECRET_VALUE)
        # remove job definitions from metronome
        for job in test_jobs:
            sdk_jobs.remove_job(job)


@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_backup_and_restore_to_s3_with_auth() -> None:
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

    config.run_backup_and_restore_with_auth(
        config.SERVICE_NAME,
        "backup-s3",
        "restore-s3",
        plan_parameters,
        config.get_foldered_node_address(),
    )


@pytest.mark.azure
@no_strict_for_azure
@pytest.mark.sanity
def test_backup_and_restore_to_azure_with_auth() -> None:
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

    config.run_backup_and_restore_with_auth(
        config.SERVICE_NAME,
        "backup-azure",
        "restore-azure",
        plan_parameters,
        config.get_foldered_node_address(),
    )


@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_unauthorized_users() -> None:
    tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME, "node-0")[0]
    _, stdout, stderr = sdk_cmd.run_cli(
        "task exec {} bash -c 'export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jdk*/) ;  export PATH=$MESOS_SANDBOX/python-dist/bin:$PATH ; export PATH=$(ls -d $MESOS_SANDBOX/apache-cassandra-*/bin):$PATH ; cqlsh -u dcossuperuser -p wrongpassword -e \"SHOW VERSION\" node-0-server.$FRAMEWORK_HOST $CASSANDRA_NATIVE_TRANSPORT_PORT' ".format(
            tasks.id
        )
    )


def create_secret(secret_value: str, secret_path: str) -> None:

    install_enterprise_cli()
    delete_secret(secret=secret_path)
    sdk_cmd.run_cli(
        'security secrets create --value="{account}" "{secret}"'.format(
            account=secret_value, secret=secret_path
        )
    )


def delete_secret(secret: str) -> None:

    sdk_cmd.run_cli("security secrets delete {}".format(secret))


def install_enterprise_cli(force=False):
    """ Install the enterprise CLI if required """

    if not force:
        _, stdout, _ = sdk_cmd.run_cli("security --version", print_output=False)
        if stdout:
            log.info("DC/OS enterprise version %s CLI already installed", stdout.strip())

    @retrying.retry(
        stop_max_attempt_number=3,
        wait_fixed=2000,
        retry_on_exception=lambda e: isinstance(e, Exception),
    )
    def _install_impl():
        sdk_cmd.run_cli("package install --yes --cli dcos-enterprise-cli", check=True)

    _install_impl()
