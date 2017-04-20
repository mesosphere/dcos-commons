import pytest

import sdk_install as install
import sdk_plan as plan
import sdk_utils

from tests.config import (
    PACKAGE_NAME
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    options = {
        "service": {
            "spec_file": "examples/web-url.yml"
        }
    }

    # this config produces 1 hello's + 0 world's:
    install.install(PACKAGE_NAME, 1, additional_options=options)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_deploy():
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    deployment_plan = plan.get_deployment_plan(PACKAGE_NAME).json()
    sdk_utils.out("deployment_plan: " + str(deployment_plan))

    assert(len(deployment_plan['phases']) == 1)
    assert(deployment_plan['phases'][0]['name'] == 'hello')
    assert(len(deployment_plan['phases'][0]['steps']) == 1)

