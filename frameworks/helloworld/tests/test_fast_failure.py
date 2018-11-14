import pytest
from sdk.testing import sdk_utils
from sdk.testing import sdk_install
from sdk.testing import sdk_plan
from tests import config


@pytest.mark.smoke
@pytest.mark.sanity
def test_finish_install_on_failure():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
    with pytest.raises(sdk_plan.TaskFailuresExceededException):
        sdk_install.install(
            config.PACKAGE_NAME,
            foldered_name,
            1,
            additional_options={
                "service": {"name": foldered_name, "yaml": "non_recoverable_state"}
            },
        )
    sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
