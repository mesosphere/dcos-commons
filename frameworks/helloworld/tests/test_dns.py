import dcos
import pytest
import shakedown

import sdk_install as install
import sdk_plan as plan
import sdk_spin as spin

from tests.config import (
    PACKAGE_NAME
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    options = {
        "service": {
            "spec_file": "examples/dns.yml"
        }
    }

    install.install(PACKAGE_NAME, 2, additional_options=options)


@pytest.mark.sanity
def test_task_dns_prefix_points_to_all_tasks():
    pod_info = dcos.http.get(
        shakedown.dcos_service_url(PACKAGE_NAME) +
        "/v1/pods/{}/info".format("hello-0")).json()

    # Assert that DiscoveryInfo is correctly set on tasks.
    assert(all(p["info"]["discovery"]["name"] == "hello" for p in pod_info))
    # Assert that the hello-0.hello-world.mesos DNS entry points to the right IP.
    spin.time_wait_noisy(lambda: (
        plan.get_deployment_plan(PACKAGE_NAME).json()['status'] == 'COMPLETE'))
