import pytest

import sdk_install as install
import sdk_utils as utils
import shakedown
from tests.config import (
    check_running,
    DEFAULT_TASK_COUNT,
    PACKAGE_NAME
)

def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    install.install(
        PACKAGE_NAME,
        DEFAULT_TASK_COUNT,
        service_name=PACKAGE_NAME,
        additional_options={"service": { "spec_file": "examples/pre-reserved.yml"} })


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
@utils.dcos_1_10_or_higher
def test_install():
    check_running(PACKAGE_NAME)
