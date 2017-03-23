import pytest

import sdk_install as install

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

SERVICE_NAME = "hdfs2"


def setup_module(module):
    install.uninstall(PACKAGE_NAME, SERVICE_NAME)
    options = {
        "service": {
            "name": SERVICE_NAME
        }
    }
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, SERVICE_NAME, additional_options=options)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME, SERVICE_NAME)


@pytest.mark.sanity
def test_deploy():
    pass
