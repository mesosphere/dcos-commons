import logging
import os
import pytest
from typing import Iterator, Optional

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_utils
from tests import config

log = logging.getLogger(__name__)

POD_NAMES = ["hello-0", "world-0", "world-1"]
REMOTE_REGION = os.environ.get("REMOTE_REGION") or None


def remote_region_enabled() -> bool:
    return REMOTE_REGION is not None


@pytest.fixture
def local_service() -> Iterator[None]:
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            3,
            additional_options={
                "service": {"scenario": "MULTI_REGION", "allow_region_awareness": True}
            },
        )

        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture
def remote_service() -> Iterator[None]:
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            3,
            additional_options={
                "service": {
                    "scenario": "MULTI_REGION",
                    "allow_region_awareness": True,
                    "region": REMOTE_REGION,
                }
            },
        )

        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


# Unlike the following tests, this one does not require that a remote region be configured
@pytest.mark.dcos_min_version("1.11")
@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_nodes_deploy_to_local_region_by_default(configure_universe: None, local_service: None) -> None:
    # Fetch master's region name: this is defined to be the local region
    local_region = sdk_cmd.cluster_request("GET", "/mesos/state").json()["domain"]["fault_domain"][
        "region"
    ]["name"]

    for pod_name in POD_NAMES:
        pod_region = get_pod_region(config.SERVICE_NAME, pod_name)

        assert pod_region == local_region


@pytest.mark.dcos_min_version("1.11")
@pytest.mark.sanity
@pytest.mark.skipif(
    not remote_region_enabled(),
    reason="REMOTE_REGION is not configured: remote nodes needed for test",
)
@sdk_utils.dcos_ee_only
def test_nodes_can_deploy_to_remote_region(configure_universe: None, remote_service: None) -> None:
    for pod_name in POD_NAMES:
        pod_region = get_pod_region(config.SERVICE_NAME, pod_name)

        assert pod_region == REMOTE_REGION


@pytest.mark.dcos_min_version("1.11")
@pytest.mark.sanity
@pytest.mark.skipif(
    not remote_region_enabled(),
    reason="REMOTE_REGION is not configured: remote nodes needed for test",
)
@sdk_utils.dcos_ee_only
def test_region_config_update_does_not_succeed(configure_universe: None, local_service: None) -> None:
    assert isinstance(REMOTE_REGION, str)
    change_region_config(REMOTE_REGION)
    sdk_plan.wait_for_plan_status(config.SERVICE_NAME, "deploy", "ERROR", timeout_seconds=180)

    change_region_config(None)
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME, timeout_seconds=180)


def change_region_config(region_name: Optional[str]) -> None:
    service_config = sdk_marathon.get_config(config.SERVICE_NAME)
    if region_name is None:
        del service_config["env"]["SERVICE_REGION"]
    else:
        service_config["env"]["SERVICE_REGION"] = region_name

    sdk_marathon.update_app(service_config, wait_for_completed_deployment=False)


def get_pod_region(service_name: str, pod_name: str) -> str:
    info = sdk_cmd.service_request("GET", service_name, "/v1/pod/{}/info".format(pod_name)).json()[
        0
    ]["info"]

    return [l["value"] for l in info["labels"]["labels"] if l["key"] == "offer_region"][0]
