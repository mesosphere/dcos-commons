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
    task_count = 3
    custom_domain = sdk_hosts.get_crypto_id_domain()
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        task_count,
        additional_options={
            "service": {
                "security": {
                    "custom_domain": custom_domain
                }
            }
        }
    )

    # Verify the endpoint entry is correct
    assert set(["native-client"]) == set(sdk_networks.get_endpoint_names(config.PACKAGE_NAME, config.SERVICE_NAME))
    test_endpoint = sdk_networks.get_endpoint(config.PACKAGE_NAME, config.SERVICE_NAME, "native-client")
    assert set(["address", "dns"]) == set(test_endpoint.keys())

    assert len(test_endpoint["address"]) == task_count
    # Expect ip:port:
    for entry in test_endpoint["address"]:
        assert len(entry.split(":")) == 2

    assert len(test_endpoint["dns"]) == task_count
    # Expect custom domain:
    for entry in test_endpoint["dns"]:
        assert custom_domain in entry
