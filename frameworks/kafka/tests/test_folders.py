import json
import pytest
import shakedown

import sdk_install as install
import sdk_tasks as tasks
from tests.test_utils import (
    DEFAULT_BROKER_COUNT,
    PACKAGE_NAME,
    SERVICE_NAME,
    broker_count_check,
    service_cli
)

FOLDERED_SERVICE_NAME = "/path/to/" + SERVICE_NAME


def uninstall_foldered():
    install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    uninstall_foldered()


def teardown_module(module):
    uninstall_foldered()


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.speedy
def test_install_foldered():
    install.install(
        PACKAGE_NAME,
        DEFAULT_BROKER_COUNT,
        service_name=FOLDERED_SERVICE_NAME,
        additional_options={"service": { "name": FOLDERED_SERVICE_NAME } })
    tasks.check_running(FOLDERED_SERVICE_NAME, DEFAULT_BROKER_COUNT)

    # test that we can access the scheduler as well:
    jsonobj = service_cli('pods list', service_name=FOLDERED_SERVICE_NAME)
    broker_count_check(len(jsonobj), service_name=FOLDERED_SERVICE_NAME)
