import pytest

import sdk_install as install
import sdk_plan as plan
import sdk_spin as spin

from tests.config import (
    PACKAGE_NAME
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    options = {
        "service": {
            "spec_file": "examples/cni.yml"
        }
    }

    install.install(PACKAGE_NAME, 1, additional_options=options)
    print('done installing...')


@pytest.mark.sanity
def test_deploy():
    deployment_plan = plan.get_deployment_plan(PACKAGE_NAME).json()
    print("deployment_plan: " + str(deployment_plan))

    assert(len(deployment_plan['phases']) == 1)
    assert(deployment_plan['phases'][0]['name'] == 'all-deploy')
    assert(len(deployment_plan['phases'][0]['steps']) == 2)


@pytest.mark.sanity
def test_joins_overlay_network():
    spin.time_wait_noisy(lambda: (
        plan.get_deployment_plan(PACKAGE_NAME).json()['status'] == 'COMPLETE'))
