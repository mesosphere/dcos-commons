import pytest

import sdk_install as install
import sdk_utils as utils

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

SERVICE_NAME = "hdfs2"


def setup_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)
    utils.gc_frameworks()
    options = {
        "service": {
            "name": SERVICE_NAME
        }
    }
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, SERVICE_NAME, additional_options=options)


def teardown_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)


@pytest.mark.sanity
def test_deploy():
    pass
