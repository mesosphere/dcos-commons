""" Make sure that you have at least 2 private nodes in your cluster for this test
    Test to
"""
import pytest

import sdk_install as install
import sdk_utils as utils
import sdk_tasks as tasks
import sdk_package as package
import shakedown

from tests.config import (
    PACKAGE_NAME,
)
TASK_COUNT = 1
SERVICE_NAMES = ["firstservice", "secondservice"]

def teardown_module(module):
    uninstall_two_cockroach_services()

def setup_module(module):
    uninstall_two_cockroach_services()

def uninstall_two_cockroach_services():
    for SERVICE_NAME in SERVICE_NAMES:
        install.uninstall(SERVICE_NAME, package_name=PACKAGE_NAME)
    utils.gc_frameworks()

# -------------------------------------------------------------

@pytest.mark.beta
@pytest.mark.sanity
def test_multi_install():
    for SERVICE_NAME in SERVICE_NAMES:
        install.install(
            PACKAGE_NAME,
            TASK_COUNT,
            service_name=SERVICE_NAME,
            check_suppression=False,
            additional_options={
                                "service": { "name": SERVICE_NAME },
                                "node": { "count": TASK_COUNT }
                                }, #overrides the marathon configuration
            package_version="1.0.1")
        tasks.check_running(SERVICE_NAME, TASK_COUNT)
