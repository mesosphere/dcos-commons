import logging

import pytest

import sdk_hosts
import sdk_install
import sdk_networks

from tests import config


log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_custom_domain():

    # sum of default pod counts, with one task each:
    # - master: 3
    # - data: 2
    # - ingest: 0
    # - coordinator: 1
    DEFAULT_TASK_COUNT = 6
    task_count_master = 3
    task_count_data = 2
    task_count_coordinator = 1

    custom_domain = sdk_hosts.get_crypto_id_domain()
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        DEFAULT_TASK_COUNT,
        additional_options={
            "service": {
                "security": {
                    "custom_domain": custom_domain
                }
            }
        }
    )

    # Verify the endpoint entry is correct
    endpoints_all = set(["coordinator-http", "coordinator-transport", "data-http", "data-transport", "master-http", "master-transport"])
    assert endpoints_all == set(sdk_networks.get_endpoint_names(config.PACKAGE_NAME, config.SERVICE_NAME))

    for endpoint in endpoints_all:
        test_endpoint = sdk_networks.get_endpoint(config.PACKAGE_NAME, config.SERVICE_NAME, endpoint)
        assert set(["address", "dns", "vip"]) == set(test_endpoint.keys())
        if "coordinator" in endpoint:
            assert len(test_endpoint["address"]) == task_count_coordinator
            assert len(test_endpoint["dns"]) == task_count_coordinator
        elif "data" in endpoint:
            assert len(test_endpoint["address"]) == task_count_data
            assert len(test_endpoint["dns"]) == task_count_data
        elif "master" in endpoint:
            assert len(test_endpoint["address"]) == task_count_master
            assert len(test_endpoint["dns"]) == task_count_master
    # Expect ip:port:
        for entry in test_endpoint["address"]:
            assert len(entry.split(":")) == 2
    # Expect custom domain:
        for entry in test_endpoint["dns"]:
            assert custom_domain in entry
