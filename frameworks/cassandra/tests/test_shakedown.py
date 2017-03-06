import pytest

from tests.config import *
import sdk_install as install
import sdk_tasks as tasks
import sdk_utils as utils


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
def test_service_health():
    check_dcos_service_health()

