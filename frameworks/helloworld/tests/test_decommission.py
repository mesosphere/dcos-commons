import logging

import pytest
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_upgrade
import sdk_utils
from tests import config


log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
        sdk_upgrade.test_upgrade(
            config.PACKAGE_NAME,
            foldered_name,
            config.DEFAULT_TASK_COUNT,
            additional_options={
                "service": {
                    "name": foldered_name,
                    "scenario": "CUSTOM_DECOMMISSION"
                }
            })

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.mark.sanity
def test_custom_decommission():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    marathon_config = sdk_marathon.get_config(foldered_name)
    marathon_config['env']['WORLD_COUNT'] = '1'
    sdk_marathon.update_app(foldered_name, marathon_config)

    sdk_plan.wait_for_completed_plan(foldered_name, 'decommission')
    decommission_plan = sdk_plan.get_decommission_plan(foldered_name)
    log.info(sdk_plan.plan_string('decommission', decommission_plan))

    custom_step_name = decommission_plan['phases'][0]['steps'][0]['name']
    assert "custom_decommission_step" == custom_step_name

    # scale back up
    marathon_config = sdk_marathon.get_config(foldered_name)
    marathon_config['env']['WORLD_COUNT'] = '2'
    sdk_marathon.update_app(foldered_name, marathon_config)
    sdk_plan.wait_for_completed_deployment(foldered_name)

    # Let's decommission again!
    marathon_config = sdk_marathon.get_config(foldered_name)
    marathon_config['env']['WORLD_COUNT'] = '1'
    sdk_marathon.update_app(foldered_name, marathon_config)
    sdk_plan.wait_for_completed_deployment(foldered_name)

    sdk_plan.wait_for_completed_plan(foldered_name, 'decommission')
    decommission_plan = sdk_plan.get_decommission_plan(foldered_name)
    log.info(sdk_plan.plan_string('decommission', decommission_plan))

    custom_step_name = decommission_plan['phases'][0]['steps'][0]['name']
    assert "custom_decommission_step" == custom_step_name
