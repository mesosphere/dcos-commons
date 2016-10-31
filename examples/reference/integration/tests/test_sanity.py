import pytest
import shakedown
import inspect
import os

from tests.test_utils import (
    PACKAGE_NAME,
    check_health,
    uninstall,
)


strict_mode = os.getenv('SECURITY', 'permissive')


def setup_module(module):
    uninstall()

    if strict_mode == 'strict':
        shakedown.install_package_and_wait(package_name=PACKAGE_NAME, options_file=os.path.dirname(os.path.abspath(inspect.getfile(inspect.currentframe()))) + "/strict.json")
    else:
        shakedown.install_package_and_wait(package_name=PACKAGE_NAME, options_file=None)

    check_health()


def teardown_module(module):
    uninstall()


@pytest.mark.sanity
def test_install_worked():
    pass
