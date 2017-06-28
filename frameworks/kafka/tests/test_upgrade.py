import pytest
import sdk_install as install
import sdk_test_upgrade
import sdk_utils as utils

from tests.test_utils import (
    PACKAGE_NAME,
    DEFAULT_BROKER_COUNT,
    SERVICE_NAME
)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        install.uninstall(PACKAGE_NAME)
        utils.gc_frameworks()

        yield # let the test session execute
    finally:
        install.uninstall(SERVICE_NAME)


@pytest.mark.upgrade
@pytest.mark.sanity
@pytest.mark.smoke
def test_upgrade_downgrade():
    options = {
        "service": {
            "beta-optin": True,
            "user":"root"
        }
    }
    sdk_test_upgrade.upgrade_downgrade("beta-{}".format(PACKAGE_NAME),
                                       PACKAGE_NAME, DEFAULT_BROKER_COUNT,
                                       additional_options=options)


@pytest.mark.soak_upgrade
def test_upgrade():
    # akin to elastic soak_test_upgrade_downgrade
    test_upgrade_downgrade()
