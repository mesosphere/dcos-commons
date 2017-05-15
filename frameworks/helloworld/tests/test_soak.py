import pytest
import sdk_test_upgrade
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
)


@pytest.mark.soak_upgrade
def test_soak_upgrade_downgrade():
    sdk_test_upgrade.soak_upgrade_downgrade(PACKAGE_NAME, PACKAGE_NAME, PACKAGE_NAME, DEFAULT_TASK_COUNT)
