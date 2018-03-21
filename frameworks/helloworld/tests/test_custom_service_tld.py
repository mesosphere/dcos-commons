import logging

import pytest
import shakedown
import time

import sdk_install
import sdk_networks

from tests import config


log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.ben
def test_custom_service_tld():
    # Go figure out the crypto id...
    ok, crypto_id = shakedown.run_command_on_master("curl localhost:62080/lashup/key/ | jq -r .zbase32_public_key")
    assert ok

    # Instead of using "autoip.dcos.thisdcos.directory", use "autoip.dcos.<cryptoid>.dcos.directory".
    # These addresses are routable in the cluster, but let us verify that we're fully replacing
    # all TLD usage with a different TLD.

    # A service yaml is used which will make sure task DNS resolves and then sleep so advertised endpoints
    # can be checked.
    custom_tld = "autoip.dcos.{}.dcos.directory".format(crypto_id.strip())
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        1,
        additional_options={"service": {
            "custom_service_tld": custom_tld, "yaml": "custom_tld"
        }})

    # Verify the endpoints are correct
    endpoints = sdk_networks.get_and_test_endpoints(config.PACKAGE_NAME, config.SERVICE_NAME, "test", 2)
    for entry in endpoints["dns"]:
        assert custom_tld in entry
