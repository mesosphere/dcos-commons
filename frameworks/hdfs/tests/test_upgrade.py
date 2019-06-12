import logging
import xml.etree.ElementTree as etree

import pytest
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_metrics
import sdk_networks
import sdk_plan
import sdk_recovery
import sdk_tasks
import sdk_upgrade
import sdk_utils

from tests import config

log = logging.getLogger(__name__)

foldered_name = config.FOLDERED_SERVICE_NAME


@pytest.mark.sanity
def test_hdfs_upgrade():
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        if sdk_utils.dcos_version_less_than("1.9"):
            # HDFS upgrade in 1.8 is not supported.
            sdk_install.install(
                config.PACKAGE_NAME,
                foldered_name,
                config.DEFAULT_TASK_COUNT,
                additional_options={"service": {"name": foldered_name}},
                timeout_seconds=30 * 60,
            )
        else:
            sdk_upgrade.test_upgrade(
                config.PACKAGE_NAME,
                foldered_name,
                config.DEFAULT_TASK_COUNT,
                from_options={"service": {"name": foldered_name}},
                timeout_seconds=30 * 60,
            )

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
