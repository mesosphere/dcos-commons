import pytest

import sdk_install
import sdk_upgrade
import sdk_utils
from tests.config import *


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        sdk_install.uninstall(PACKAGE_NAME, package_name=PACKAGE_NAME)
        sdk_utils.gc_frameworks()

        # TODO: This test is only here because we can't currently test upgrades on a foldered service
        # After the next beta-hdfs release (with folder support), delete test_upgrade.py and uncomment the upgrade test in test_shakedown.py
        sdk_upgrade.test_upgrade(
            "beta-{}".format(PACKAGE_NAME),
            PACKAGE_NAME,
            DEFAULT_TASK_COUNT,
            service_name=PACKAGE_NAME,
            additional_options={"service": {"name": PACKAGE_NAME} },
            test_version_options={"service": {"name": PACKAGE_NAME, "user": "root"} })

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME, package_name=PACKAGE_NAME)


@pytest.mark.sanity
def test_upgrade():
    check_healthy()
