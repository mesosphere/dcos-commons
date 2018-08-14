import pytest
import sdk_utils
import sdk_install
from tests import config


@pytest.mark.smoke
@pytest.mark.sanity
def test_finish_install_on_failure(configure_security):
    try:
        foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
        sdk_install.install(
            config.PACKAGE_NAME,
            foldered_name,
            config.DEFAULT_TASK_COUNT,
            additional_options={
                "service": {"name": foldered_name, "yaml": "non_recoverable_state"}
            },
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
