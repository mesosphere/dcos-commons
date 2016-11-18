import pytest
import shakedown
import inspect
import os

from tests.test_utils import (
    PACKAGE_NAME,
    check_health,
    install,
    uninstall,
)


def setup_module(module):
    uninstall()
    install()
    check_health()


def teardown_module(module):
    uninstall()


@pytest.mark.sanity
def test_install_worked():
    pass
