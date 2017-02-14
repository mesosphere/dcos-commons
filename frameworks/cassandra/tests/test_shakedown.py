import pytest
import shakedown

from tests.config import *
import sdk_install as install
import sdk_tasks as tasks


def setup_module(module):
    shakedown.uninstall_package_and_data(PACKAGE_NAME, PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    shakedown.uninstall_package_and_data(PACKAGE_NAME, PACKAGE_NAME)


@pytest.mark.sanity
def test_service_health():
    check_dcos_service_health()

