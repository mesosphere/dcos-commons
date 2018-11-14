import logging

import pytest

from sdk.testing import sdk_install
from sdk.testing import sdk_plan
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            2,
            additional_options={"service": {"yaml": "share_pid_namespace"}},
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_deploy():
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    deployment_plan = sdk_plan.get_deployment_plan(config.SERVICE_NAME)
    log.info(sdk_plan.plan_string("deploy", deployment_plan))

    assert len(deployment_plan["phases"]) == 2
    assert deployment_plan["phases"][0]["name"] == "server-deploy"
    assert deployment_plan["phases"][1]["name"] == "once-deploy"
    assert len(deployment_plan["phases"][0]["steps"]) == 2
    assert len(deployment_plan["phases"][1]["steps"]) == 2
