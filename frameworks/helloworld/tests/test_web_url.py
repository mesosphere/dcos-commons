import logging

import pytest

import sdk_install
import sdk_plan

from tests.config import (
    PACKAGE_NAME
)

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME)
        options = {
            "service": {
                "spec_file": "examples/web-url.yml"
            }
        }

        # this config produces 1 hello's + 0 world's:
        sdk_install.install(PACKAGE_NAME, 1, additional_options=options)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_deploy():
    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)
    deployment_plan = sdk_plan.get_deployment_plan(PACKAGE_NAME)
    log.info("deployment_plan: " + str(deployment_plan))

    assert(len(deployment_plan['phases']) == 1)
    assert(deployment_plan['phases'][0]['name'] == 'hello')
    assert(len(deployment_plan['phases'][0]['steps']) == 1)
