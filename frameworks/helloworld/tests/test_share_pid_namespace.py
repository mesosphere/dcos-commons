import logging

import pytest

import sdk_install
import sdk_plan
import sdk_marathon


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
                "spec_file": "examples/share_pid_namespace.yml"
            }
        }

        sdk_install.install(PACKAGE_NAME, 2, additional_options=options)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_deploy():
    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)
    deployment_plan = sdk_plan.get_deployment_plan(PACKAGE_NAME)
    log.info("deployment plan: " + str(deployment_plan))

    assert(len(deployment_plan['phases']) == 2)
    assert(deployment_plan['phases'][0]['name'] == 'server-deploy')
    assert(deployment_plan['phases'][1]['name'] == 'once-deploy')
    assert(len(deployment_plan['phases'][0]['steps']) == 2)
    assert(len(deployment_plan['phases'][1]['steps']) == 2)
