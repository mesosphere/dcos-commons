import logging

import pytest
import sdk_install
import sdk_upgrade
import sdk_utils

from tests import config

log = logging.getLogger(__name__)

foldered_name = config.FOLDERED_SERVICE_NAME


@pytest.mark.sanity
def test_hdfs_upgrade():
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        sdk_upgrade.test_upgrade(
            config.PACKAGE_NAME,
            foldered_name,
            config.DEFAULT_TASK_COUNT,
            from_options={"service": {"name": foldered_name}},
            timeout_seconds=30 * 60,
        )

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
