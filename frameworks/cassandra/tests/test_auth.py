import retrying
from typing import Any, Dict, Iterator, List
import pytest
import sdk_jobs
import sdk_cmd
import sdk_install
from tests import config


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security: None) -> Iterator[None]:
    test_jobs: List[Dict[str, Any]] = []
    try:
        test_jobs = config.get_all_jobs(auth=True)
        # destroy/reinstall any prior leftover jobs, so that they don't touch the newly installed service:
        for job in test_jobs:
            sdk_jobs.install_job(job)

        create_secret(
            secret_value=config.SECRET_VALUE, secret_path=config.PACKAGE_NAME + '/' + config.SECRET_VALUE
        )
        service_options = {
            "service": {
                "name": config.SERVICE_NAME,
                "security": {"authentication": {"enabled": True}, "authorization": {"enabled": True}},
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

        # remove job definitions from metronome
        for job in test_jobs:
            sdk_jobs.remove_job(job)


@pytest.mark.sanity
def test_auth() -> None:
    config.verify_client_can_write_read_and_delete_with_auth(
        config.get_foldered_node_address(),
    )


def create_secret(secret_value: str, secret_path: str) -> None:

    install_enterprise_cli()
    delete_secret(secret=secret_path)
    sdk_cmd.run_cli(
        'security secrets create --value="{account}" "{secret}"'.format(
            account=secret_value, secret=secret_path
        )
    )

    # delete_secret(secret=secret_path)


def delete_secret(secret: str) -> None:

    sdk_cmd.run_cli("security secrets delete {}".format(secret))


def install_enterprise_cli(force=False):
    """ Install the enterprise CLI if required """

    if not force:
        _, stdout, _ = sdk_cmd.run_cli("security --version", print_output=False)
        # if stdout:
        #     log.info("DC/OS enterprise version %s CLI already installed", stdout.strip())
        #     return

    @retrying.retry(
        stop_max_attempt_number=3,
        wait_fixed=2000,
        retry_on_exception=lambda e: isinstance(e, Exception),
    )
    def _install_impl():
        sdk_cmd.run_cli("package install --yes --cli dcos-enterprise-cli", check=True)

    _install_impl()
