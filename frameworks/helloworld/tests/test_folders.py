import json
import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    configured_task_count,
    check_running
)

FOLDERED_SERVICE_NAME = "/path/to/" + PACKAGE_NAME


def uninstall_foldered():
    install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)


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

    # test that we can access the scheduler as well:
    stdout = cmd.run_cli('{} --name={} pods list'.format(PACKAGE_NAME, FOLDERED_SERVICE_NAME))
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == configured_task_count(FOLDERED_SERVICE_NAME)
