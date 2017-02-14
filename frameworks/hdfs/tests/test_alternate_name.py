import pytest
import shakedown

import sdk_install as install
import sdk_plan as plan

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

SERVICE_NAME="hdfs2"


def setup_module(module):
    shakedown.uninstall_package_and_data(PACKAGE_NAME, SERVICE_NAME)
    options = {
        "service": {
            "name": "hdfs2"
        }
    }
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, SERVICE_NAME, additional_options=options)


def teardown_module(module):
    shakedown.uninstall_package_and_data(PACKAGE_NAME, SERVICE_NAME)


@pytest.mark.sanity
def test_deploy():
    pass
