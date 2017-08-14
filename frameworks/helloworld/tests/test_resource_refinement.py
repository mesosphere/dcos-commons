import pytest
import sdk_install
import sdk_utils
import shakedown  # required by @sdk_utils.dcos_X_Y_or_higher
from tests import config


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME)
        options = {
            "service": {
                "spec_file": "examples/pre-reserved.yml"
            }
        }

        sdk_install.install(config.PACKAGE_NAME, config.DEFAULT_TASK_COUNT, additional_options=options)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
@sdk_utils.dcos_1_10_or_higher
def test_install():
    config.check_running(config.PACKAGE_NAME)
