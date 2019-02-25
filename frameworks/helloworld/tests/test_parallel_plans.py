import logging
import pytest

import sdk_cmd
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

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.mark.sanity
def test_all_tasks_are_launched():
    service_options = {"service": {"yaml": "sidecar"}, "hello": {"count": 1}}
    sdk_install.install(
        config.PACKAGE_NAME,
        foldered_name,
        0,
        additional_options=service_options,
        wait_for_deployment=False,
        wait_for_all_conditions=True
    )
    # after above method returns, start all plans right away.
    sdk_plan.start_plan(foldered_name, "sidecar")
    sdk_plan.start_plan(foldered_name, "sidecar-parameterized", {"PLAN_PARAMETER": "parameterized"})
    sdk_plan.wait_for_completed_plan(foldered_name, "sidecar")
    sdk_plan.wait_for_completed_plan(foldered_name, "sidecar-parameterized")
    # Assert all the tasks have non-empty taskIds in TaskInfo and Status objects
    # Note that this is fetched from SDK Persistence storage layer.
    pod_hello_0_info = sdk_cmd.service_request(
        "GET", foldered_name, "/v1/pod/hello-0/info"
    ).json()
    for taskInfoAndStatus in pod_hello_0_info:
        info = taskInfoAndStatus["info"]
        status = taskInfoAndStatus["status"]
        # We always have the TaskInfo object (created during launch),
        # but we may or may not have TaskStatus based on whether the
        # task was launched and we received an update from mesos or not.
        if status:
            assert info["taskId"]["value"] == status["taskId"]["value"]
            assert len(info["taskId"]["value"]) > 0
        else:
            assert len(info["taskId"]["value"]) == 0
