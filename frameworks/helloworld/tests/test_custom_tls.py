import logging

import pytest
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_security
import sdk_utils

from tests import config
from security import transport_encryption

# Service discovery prefix for the `discovery` pod which allows testing updates.
DISCOVERY_TASK_PREFIX = "discovery-prefix"

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        # Create service account
        sdk_security.create_service_account(
            service_account_name=config.SERVICE_NAME,
            service_account_secret=config.SERVICE_NAME + "-secret",
        )
        sdk_cmd.run_cli(
            "security org groups add_user superusers {name}".format(name=config.SERVICE_NAME)
        )

        # Create as secret based on the private key
        sdk_cmd.run_cli(
            "secrets create -f {service_name} {service_name}/{service_name}-private-key.pem"
                .format(service_name=config.SERVICE_NAME))

        yield  # let the test session execute

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_security.delete_service_account(
            service_account_name=config.SERVICE_NAME,
            service_account_secret=config.SERVICE_NAME + "-secret",
        )
        sdk_security.delete_secret("{service_name}/{service_name}-private-key.pem"
                .format(service_name=config.SERVICE_NAME))


@pytest.mark.test_custom_tls
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version("1.10")
def test_custom_tls_no_mount_path():

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        1,
        additional_options={
            "service": {
                "yaml": "custom_tls",
                "service_account": config.SERVICE_NAME,
                "service_account_secret": config.SERVICE_NAME + "-secret"
            },
            "tls": {
                "discovery_task_prefix": DISCOVERY_TASK_PREFIX
                "custom": {
                    "artifact_name": "{service_name}-custom-key.pem"
                        .format(service_name=config.SERVICE_NAME),
                    "artifact_secret": "{service_name}/{service_name}-private-key"
                        .format(service_name=config.SERVICE_NAME)
                }
            },
        },
    )

    # Task will fail if custom artifact isn't loaded.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    # Uninstall when plan is complete. 
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.test_custom_tls
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version("1.10")
def test_tls_basic_artifacts():
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        1,
        additional_options={
            "service": {
                "yaml": "custom_tls",
                "service_account": config.SERVICE_NAME,
                "service_account_secret": config.SERVICE_NAME + "-secret"
            },
            "tls": {
                "discovery_task_prefix": DISCOVERY_TASK_PREFIX
                "custom": {
                    "artifact_name": "{service_name}-custom-key.pem"
                        .format(service_name=config.SERVICE_NAME),
                    "artifact_secret": "{service_name}/{service_name}-private-key"
                        .format(service_name=config.SERVICE_NAME),
                    "artifact_mount_path": "ssl/{service_name}-custom-key.pem"
                        .format(service_name=config.SERVICE_NAME),
                }
            },
        },
    )

    # Task will fail if custom artifact isn't loaded.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    # Uninstall when plan is complete. 
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
