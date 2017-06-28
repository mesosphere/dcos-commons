import dcos
import pytest
import shakedown

import sdk_install as install
import sdk_plan as plan

from tests.config import (
    PACKAGE_NAME
)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        install.uninstall(PACKAGE_NAME)
        options = {
            "service": {
                "spec_file": "examples/discovery.yml"
            }
        }

        install.install(PACKAGE_NAME, 1, additional_options=options)

        yield # let the test session execute
    finally:
        install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_task_dns_prefix_points_to_all_tasks():
    pod_info = dcos.http.get(
        shakedown.dcos_service_url(PACKAGE_NAME) +
        "/v1/pods/{}/info".format("hello-0")).json()

    # Assert that DiscoveryInfo is correctly set on tasks.
    assert(all(p["info"]["discovery"]["name"] == "hello-0" for p in pod_info))
    # Assert that the hello-0.hello-world.mesos DNS entry points to the right IP.
    plan.wait_for_completed_deployment(PACKAGE_NAME)
