import pytest
import sdk_install
import sdk_utils
from tests import config


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME))

        # note: this package isn't released to universe, so there's nothing to test_upgrade() with
        sdk_install.install(
            config.PACKAGE_NAME,
            sdk_utils.get_foldered_name(config.SERVICE_NAME),
            config.DEFAULT_TASK_COUNT,
            additional_options={"service": { "name": sdk_utils.get_foldered_name(config.SERVICE_NAME) } })

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.sanity
@pytest.mark.smoke
def test_install():
    pass # package installed and appeared healthy!
