import pytest

from tests.test_utils import (
    check_health,
    install,
    uninstall
)


def setup_module(module):
    uninstall()
    install()
    check_health()


@pytest.mark.sanity
def test_install():
    pass
