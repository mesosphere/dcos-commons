import pytest
import shakedown

import sdk_install as install

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)


def setup_module(module):
    shakedown.uninstall_package_and_data(PACKAGE_NAME, PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


@pytest.mark.sanity
@pytest.mark.smoke
def test_install():
    pass # package installed and appeared healthy!
