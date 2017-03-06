import pytest
import shakedown

from tests.config import *
import sdk_install as install


def setup_module(module):
    shakedown.uninstall_package_and_data(PACKAGE_NAME, PACKAGE_NAME)
    install.gc_frameworks()
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def setup_function(function):
    shakedown.wait_for_service_tasks_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    shakedown.uninstall_package_and_data(PACKAGE_NAME, PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
def test_service_health():
    check_dcos_service_health()

