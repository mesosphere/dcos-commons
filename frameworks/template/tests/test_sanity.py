import pytest

import sdk_install
import sdk_plan
import sdk_utils

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

FOLDERED_SERVICE_NAME = sdk_utils.get_foldered_name(PACKAGE_NAME)

def setup_module(module):
    sdk_install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)
    sdk_utils.gc_frameworks()
    sdk_install.install(
        PACKAGE_NAME,
        DEFAULT_TASK_COUNT,
        service_name=FOLDERED_SERVICE_NAME,
        additional_options={"service": { "name": FOLDERED_SERVICE_NAME } })
    sdk_plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME)


def teardown_module(module):
    sdk_install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
def test_install():
    pass # package installed and appeared healthy!
