import logging
import re

import dcos.marathon
import pytest
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils
import shakedown
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
def test_custom_plan():
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        3,
        additional_options={"service": {"customize_plan": True}})

    plan = sdk_plan.get_deployment_plan(config.SERVICE_NAME)
    world_steps = plan['phases'][1]['steps']
    assert world_steps[0]['name'] == 'world-1:[server]'
    assert world_steps[1]['name'] == 'world-0:[server]'

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
