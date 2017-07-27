import pytest

import sdk_install
import sdk_upgrade
import sdk_utils
from tests.config import *


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        sdk_install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)
        sdk_utils.gc_frameworks()

        # TODO: fails due to released beta-hdfs not supporting foldered names.
        sdk_upgrade.test_upgrade(
            "beta-{}".format(PACKAGE_NAME),
            PACKAGE_NAME,
            DEFAULT_TASK_COUNT,
            service_name=FOLDERED_SERVICE_NAME,
            additional_options={"service": {"name": FOLDERED_SERVICE_NAME}})

        yield # let the test session execute
    finally:
        sdk_install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)


@pytest.mark.sanity
def test_upgrade():
    check_healthy()
