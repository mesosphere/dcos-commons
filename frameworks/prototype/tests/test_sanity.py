import pytest

import sdk_install as install
import sdk_utils as utils

from tests.config import (
    PACKAGE_NAME
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
def test_install():
    pass # package installed and appeared healthy!
