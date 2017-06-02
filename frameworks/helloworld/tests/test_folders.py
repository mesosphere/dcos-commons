import pytest
import shakedown

import sdk_install as install
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    check_running
)

FOLDERED_SERVICE_NAME = "/path/to/" + PACKAGE_NAME


def uninstall_foldered():
    install.uninstall(
        FOLDERED_SERVICE_NAME,
        package_name=PACKAGE_NAME,
        role=FOLDERED_SERVICE_NAME.lstrip('/') + '-role',
        principal=FOLDERED_SERVICE_NAME + '-principal',
        zk='dcos-service-' + FOLDERED_SERVICE_NAME.lstrip('/').replace('/', '__'))


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    uninstall_foldered()


def teardown_module(module):
    uninstall_foldered()


@pytest.mark.sanity
@pytest.mark.smoke
def test_install_foldered():
    install.install(
        PACKAGE_NAME,
        DEFAULT_TASK_COUNT,
        service_name=FOLDERED_SERVICE_NAME,
        additional_options={"service": { "name": FOLDERED_SERVICE_NAME } })
    check_running(FOLDERED_SERVICE_NAME)
