import pytest

import sdk_install
import sdk_utils

from tests.config import (
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_TASK_COUNT
)

FOLDERED_SERVICE_NAME = sdk_utils.get_foldered_name(SERVICE_NAME)

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME, FOLDERED_SERVICE_NAME)

        # note: this package isn't released to universe, so there's nothing to test_upgrade() with
        sdk_install.install(
            PACKAGE_NAME,
            FOLDERED_SERVICE_NAME,
            DEFAULT_TASK_COUNT,
            additional_options={"service": { "name": FOLDERED_SERVICE_NAME } })

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME, FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
def test_install():
    pass # package installed and appeared healthy!
