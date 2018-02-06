import logging

import pytest
import retrying

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_utils
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {
            "service": {
                "spec_file": "examples/pre-reserved-sidecar.yml"
            }
        }

        # this yml has 1 hello's + 0 world's:
        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 1, additional_options=options)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.dcos_min_version('1.10')
def test_deploy():
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.dcos_min_version('1.10')
def test_sidecar():
    run_plan('sidecar')


def run_plan(plan_name, params=None):
    sdk_plan.start_plan(config.SERVICE_NAME, plan_name, params)

    started_plan = sdk_plan.get_plan(config.SERVICE_NAME, plan_name)
    log.info("sidecar plan: " + str(started_plan))
    assert(len(started_plan['phases']) == 1)
    assert(started_plan['phases'][0]['name'] == plan_name + '-deploy')
    assert(len(started_plan['phases'][0]['steps']) == 1)

    sdk_plan.wait_for_completed_plan(config.SERVICE_NAME, plan_name)
