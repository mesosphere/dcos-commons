import logging
import pytest

import sdk_install
import sdk_plan


from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {"service": {"user": "root", "yaml": "resource_limits"}}

        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            expected_running_tasks=1,
            additional_options=options,
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version("2.1")
@pytest.mark.sanity
def test_resource_limits():
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
