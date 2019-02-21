import logging
import pytest

import sdk_install
import sdk_plan

from tests import config

log = logging.getLogger(__name__)


@pytest.mark.parametrize('num_times', range(10))
@pytest.mark.edgelb
def test_edgelb(num_times):
    try:
        foldered_name = "hello-world-{}".format(num_times)
        service_options = {"service": {"yaml": "edgelb"}}
        sdk_install.install(
            config.PACKAGE_NAME,
            foldered_name,
            0,
            additional_options=service_options,
            wait_for_deployment=False,
            wait_for_all_conditions=False
        )
        log.info("starting plans")
        # after above method returns, start reload plan right away.
        wait_for_plan(foldered_name, "deploy")
        sdk_plan.start_plan(foldered_name, "reload")
        wait_for_plan(foldered_name, "reload")
        sdk_plan.wait_for_completed_plan(foldered_name, "deploy")
        sdk_plan.wait_for_completed_plan(foldered_name, "reload")
        # time.sleep(1000000)  # Ctrl-C to cancel
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


def wait_for_plan(service_name, plan_name):
    return sdk_plan.wait_for_plan_status(
        service_name, plan_name, ["PENDING", "STARTING", "IN_PROGRESS", "COMPLETE"]
    )
