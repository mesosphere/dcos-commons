import pytest

import sdk_versions

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

@pytest.mark.upgrade
@pytest.mark.sanity
def test_upgrade_downgrade():
    sdk_versions.upgrade_downgrade(PACKAGE_NAME, PACKAGE_NAME, DEFAULT_TASK_COUNT, reinstall_test_version=False)
