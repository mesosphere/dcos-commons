import pytest

import sdk_install as install
import sdk_plan as plan
import sdk_utils as utils

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

FOLDERED_SERVICE_NAME = utils.get_foldered_name(PACKAGE_NAME)

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)
        utils.gc_frameworks()
        install.install(
            PACKAGE_NAME,
            DEFAULT_TASK_COUNT,
            service_name=FOLDERED_SERVICE_NAME,
            additional_options={"service": { "name": FOLDERED_SERVICE_NAME } })
        plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME)

        yield # let the test session execute
    finally:
        install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
def test_install():
    pass # package installed and appeared healthy!
