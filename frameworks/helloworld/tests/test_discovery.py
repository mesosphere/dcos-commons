from typing import Iterator

import pytest
import sdk_cmd
import sdk_install
import sdk_plan
from tests import config


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security: None) -> Iterator[None]:
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {"service": {"yaml": "discovery"}}

        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 1, additional_options=options)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_task_dns_prefix_points_to_all_tasks() -> None:
    pod_info = sdk_cmd.service_request("GET", config.SERVICE_NAME, "/v1/pod/hello-0/info").json()
    # Assert that DiscoveryInfo is correctly set on tasks.
    assert all(p["info"]["discovery"]["name"] == "hello-0" for p in pod_info)
    # Assert that the hello-0.hello-world.mesos DNS entry points to the right IP.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
