import logging
import os
import time
import pytest

import sdk_install
import sdk_plan
import sdk_utils

from tests import config


log = logging.getLogger(__name__)
foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)


@pytest.mark.edgelb
def test_edgelb():
    try:
        service_options = {"service": {"yaml": "edgelb"}}
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
        sdk_install.install(
            config.PACKAGE_NAME,
            foldered_name,
            0,
            additional_options=service_options,
            wait_for_deployment=False,
            wait_for_all_conditions=False,
        )
        # after above method returns, start reload plan right away.
        sdk_plan.wait_for_kicked_off_plan(foldered_name, "deploy")
        sdk_plan.start_plan(foldered_name, "reload")
        sdk_plan.wait_for_kicked_off_plan(foldered_name, "reload")
        time.sleep(1000000)  # Ctrl-C to cancel
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
