import logging
import pytest

import sdk_install
import sdk_metrics
import sdk_plan
import sdk_utils
from tests import config

log = logging.getLogger(__name__)
foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)


@pytest.fixture(scope="function", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


back_off_crash_loop_options = {
    "service": {"yaml": "crash-loop", "enable_backoff": True, "sleep": 60}
}


@pytest.mark.tarun
def test_default_plan_backoff():
    sdk_install.install(
        config.PACKAGE_NAME,
        foldered_name,
        expected_running_tasks=0,
        additional_options=back_off_crash_loop_options,
        wait_for_deployment=False,
        wait_for_all_conditions=False,
    )
    # State transition should be STARTING -> STARTED -> DELAYED in a loop.
    # As STARTING lasts for a very short duration, we test the switch between other two states.
    check_delayed_and_suppressed("deploy")
    sdk_plan.wait_for_plan_status(foldered_name, "deploy", "STARTED")
    check_delayed_and_suppressed("deploy")
    # We can't make further progress, this is the end of the test.


@pytest.mark.tarun
def test_recovery_backoff():
    sdk_install.install(
        config.PACKAGE_NAME,
        foldered_name,
        expected_running_tasks=0,
        additional_options=back_off_crash_loop_options,
        wait_for_deployment=False,
        wait_for_all_conditions=False,
    )
    check_delayed_and_suppressed("deploy")
    sdk_plan.force_complete_step(foldered_name, "deploy", "crash", "hello-0:[server]")
    # Deploy plan is complete. Recovery plan should take over. Recovery plan is in COMPLETE
    # by default, it should go from STARTED -> DELAYED.
    sdk_plan.wait_for_plan_status(foldered_name, "recovery", "STARTED")
    check_delayed_and_suppressed("recovery")


def check_delayed_and_suppressed(plan_name: str):
    sdk_plan.wait_for_plan_status(foldered_name, plan_name, "DELAYED")
    sdk_metrics.wait_for_scheduler_gauge_value(
        foldered_name,
        "is_suppressed",
        lambda result: isinstance(result, bool) and bool(result),  # Should be set to true.
    )
