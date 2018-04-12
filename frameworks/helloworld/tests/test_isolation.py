import logging
import re

import dcos.marathon
import pytest
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils
import shakedown
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
def test_tmp_directory_created():

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        0,
        additional_options={"service": {"name": config.SERVICE_NAME, "yaml": "isolation"}},
        wait_for_deployment=False)

    pl = sdk_plan.get_deployment_plan(config.SERVICE_NAME)

    assert pl['status'] != 'COMPLETE'

    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config['env']['HELLO_ISOLATION'] = 'true'
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)

    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
