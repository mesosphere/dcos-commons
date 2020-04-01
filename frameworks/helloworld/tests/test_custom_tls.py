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
        # need to grant access to secret-store
        sdk_cmd.run_cli(
            "security org groups add_user superusers {name}".format(name=config.SERVICE_NAME)
        )

        # Create as secret based on the private key
        sdk_cmd.run_cli(
            "security secrets create -f {service_name}-private-key.pem {service_name}/custom-secret"
                .format(service_name=config.SERVICE_NAME))

        yield  # let the test session execute

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_security.delete_service_account(
            service_account_name=config.SERVICE_NAME,
            service_account_secret=config.SERVICE_NAME + "-secret",
        )
        sdk_security.delete_secret("{service_name}/custom-secret"
                .format(service_name=config.SERVICE_NAME))


@pytest.mark.test_custom_tls
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version("1.10")
def test_custom_tls():

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
                "discovery_task_prefix": DISCOVERY_TASK_PREFIX,
            },
        },
    )

    # Task will fail if custom artifacts aren't loaded.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
