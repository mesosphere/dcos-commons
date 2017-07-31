import pytest

import sdk_install
import sdk_utils
import shakedown
from tests.config import (
    check_running,
    DEFAULT_TASK_COUNT,
    PACKAGE_NAME
)

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        sdk_install.uninstall(PACKAGE_NAME)
        sdk_install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
@sdk_utils.dcos_1_10_or_higher
def test_install():
    check_running(PACKAGE_NAME)
