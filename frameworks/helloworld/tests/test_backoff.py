import logging

import pytest
import retrying

import sdk_install
import sdk_plan
import sdk_utils
from tests import config


log = logging.getLogger(__name__)
foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.mark.sanity
def test_deploy_plan_backoff():
    sdk_install.install(
        config.PACKAGE_NAME,
        foldered_name,
        expected_running_tasks=0,
        additional_options={"service": {"yaml": "crash-loop"}},
        wait_for_deployment=False,
        wait_for_all_conditions=False
    )
    for state in ["DELAYED", "STARTED", "DELAYED"]:
        # State transition should be STARTING -> STARTED -> DELAYED in a loop.
        # As STARTING lasts for a very short duration, we test the switch between other two states.
        sdk_plan.wait_for_plan_status(
            foldered_name,
            "deploy",
            state
        )
    # We can't make further progress, this is the end of the test.


@pytest.mark.tarun
def test_recovery_backoff():
    sdk_install.install(
        config.PACKAGE_NAME,
        foldered_name,
        expected_running_tasks=0,
        additional_options={"service": {"yaml": "crash-loop"}},
        wait_for_deployment=False,
        wait_for_all_conditions=False
    )
    sdk_plan.wait_for_plan_status(foldered_name, "deploy", "DELAYED")
    # Deploy plan is complete. Recovery plan should take over.
    sdk_plan.force_complete_step(foldered_name, "deploy", "crash", "hello-0:[server]")
    # Recovery plan is in COMPLETE by default, it should go from STARTED -> DELAYED
    sdk_plan.wait_for_plan_status(foldered_name, "recovery", "STARTED")
    sdk_plan.wait_for_plan_status(foldered_name, "recovery", "DELAYED")
