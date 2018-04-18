import logging

import pytest
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_cmd
from tests import config


log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

@pytest.mark.sanity
def test_capabilitiy_escalation():

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        0,
        additional_options={"service": {"name": config.SERVICE_NAME, "yaml": "capabilities"}},
        wait_for_deployment=False)

    pl = sdk_plan.get_deployment_plan(config.SERVICE_NAME)

    assert pl['status'] != 'COMPLETE'

    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)

    #make sure multiple capabilities are parsed correctly
    marathon_config['env']['HELLO_CAPABILITIES'] = "SYS_ADMIN";

    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
