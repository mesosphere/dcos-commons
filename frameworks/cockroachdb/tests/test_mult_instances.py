
import pytest

import sdk_install as install
import sdk_utils as utils
import sdk_tasks as tasks
import sdk_package as package
import shakedown

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)
TASK_COUNT = 1
SERVICE_NAMES = ["firstservice", "secondservice"]

def teardown_module(module):
    uninstall_two_cockroach_services()
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    uninstall_two_cockroach_services()

def uninstall_two_cockroach_services():
    for SERVICE_NAME in SERVICE_NAMES:
        install.uninstall(SERVICE_NAME, package_name=PACKAGE_NAME)
    utils.gc_frameworks()

# -------------------------------------------------------------
""" Make sure that you have at least 2 private nodes in your cluster for this test to pass
"""
@pytest.mark.beta
@pytest.mark.sanity
def test_two_service_install_with_one_task_each():
    uninstall_two_cockroach_services()
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

""" Make sure that you have at least 2 * DEFAULT_TASK_COUNT in your cluster for this test to pass
"""
@pytest.mark.beta
@pytest.mark.sanity
def test_two_service_install_with_DEFAULT_TASK_COUNT_tasks_each():
    uninstall_two_cockroach_services()
    for SERVICE_NAME in SERVICE_NAMES:
        install.install(
            PACKAGE_NAME,
            DEFAULT_TASK_COUNT,
            service_name=SERVICE_NAME,
            check_suppression=False,
            additional_options={
                                "service": { "name": SERVICE_NAME } #overrides marathon configuration
                                },
            package_version="1.0.1")
        tasks.check_running(SERVICE_NAME, TASK_COUNT)
