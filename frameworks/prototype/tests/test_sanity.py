import pytest
import shakedown

import sdk_install as install

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)


def setup_module(module):
    shakedown.uninstall_package_and_data(PACKAGE_NAME, PACKAGE_NAME)
    install.gc_frameworks()
    options = {
        "service": {
            "specification_uri": "https://gist.githubusercontent.com/mohitsoni/29d03e7d73135d4a8d2ea54b508bbcf9/raw/fb495434dcc507d3afc79fa761afe57bb31975c4/service.yml"
        }
    }

    # this config produces 1 hello's + 0 world's:
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, additional_options=options)


def teardown_module(module):
    shakedown.uninstall_package_and_data(PACKAGE_NAME, PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
def test_install():
    pass  # package installed and appeared healthy!
