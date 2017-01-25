import pytest

from tests.test_utils import *


def setup_module(module):
    uninstall()
    install()
    check_health()


@pytest.mark.sanity
def test_install():
    check_dcos_service_health()
