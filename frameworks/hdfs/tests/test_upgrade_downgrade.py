import pytest

import sdk_versions
from tests.config import *

@pytest.mark.upgrade
@pytest.mark.sanity
def test_upgrade():
    options = {
        "service": {
            "name": "beta-{}".format(PACKAGE_NAME),
            "beta-optin": True,
            "user": "root",
            "principal": "{}-principal".format(PACKAGE_NAME)
        }
    }

    sdk_versions.upgrade("beta-{}".format(PACKAGE_NAME), "{}".format(PACKAGE_NAME), DEFAULT_TASK_COUNT,
            additional_options=options, reinstall_test_version=False, timeout_seconds=DEFAULT_HDFS_DEPLOYMENT_TIMEOUT)


@pytest.mark.skip(reason="Can be enabled after a new release that can handle a downgrade via the update plan")
@pytest.mark.downgrade
@pytest.mark.sanity
def test_downgrade():
    options = {
        "service": {
            "name": "beta-{}".format(PACKAGE_NAME),
            "beta-optin": True,
            "user": "root",
            "principal": "{}-principal".format(PACKAGE_NAME)
        }
    }

    sdk_versions.downgrade("beta-{}".format(PACKAGE_NAME), "beta-{}".format(PACKAGE_NAME), DEFAULT_TASK_COUNT,
            additional_options=options, reinstall_test_version=False, timeout_seconds=DEFAULT_HDFS_DEPLOYMENT_TIMEOUT)
