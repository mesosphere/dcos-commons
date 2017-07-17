import pytest

import sdk_test_upgrade
from tests.config import *


@pytest.mark.skip(reason="https://jira.mesosphere.com/browse/INFINITY-1933")
@pytest.mark.upgrade
@pytest.mark.sanity
def test_upgrade_downgrade():
    sdk_test_sdk_upgrade.upgrade_downgrade(
        "beta-{}".format(PACKAGE_NAME),
        PACKAGE_NAME,
        DEFAULT_TASK_COUNT,
        additional_options={"service": {"beta-optin": True}},
        reinstall_test_version=False)
