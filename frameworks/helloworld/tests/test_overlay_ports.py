import pytest
import json

import shakedown

import sdk_install as install
import sdk_plan as plan
import sdk_utils as utils

from tests.config import (
    PACKAGE_NAME,
    check_running

)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    options = {
        "service": {
            "spec_file": "examples/overlay_ports.yml"
        }
    }

    install.install(PACKAGE_NAME, 1, additional_options=options)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.runnow
def test_install():
    plan.wait_for_completed_deployment(PACKAGE_NAME)
