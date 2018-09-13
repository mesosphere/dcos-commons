import logging
import pytest

import sdk_hosts
import sdk_install
import sdk_networks

from tests import config


log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_custom_service_tld():
    custom_tld = sdk_hosts.get_crypto_id_domain()
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        1,
        additional_options={
            "service": {
                "custom_service_tld": custom_tld,
                "yaml": "custom_tld",
            }
        })

    # Verify the endpoints are correct
    endpoints = sdk_networks.get_and_test_endpoints(
        config.PACKAGE_NAME, config.SERVICE_NAME, "test", 2)
    for entry in endpoints["dns"]:
        assert custom_tld in entry
