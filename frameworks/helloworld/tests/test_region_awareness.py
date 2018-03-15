import json
import logging

import pytest
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_utils
from tests import config

log = logging.getLogger(__name__)

POD_NAMES = ['hello-0', 'world-0', 'world-1']


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.region_awareness
@sdk_utils.dcos_ee_only
def test_nodes_deploy_to_local_region_by_default():
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 3)

    sdk_plan.wait_for_completed_deploy(config.SERVICE_NAME)

    for pod_name in POD_NAMES:
        info = sdk_cmd.service_request(
            'GET', config.SERVICE_NAME, '/v1/pod/{}/info'.format(pod_name)
        ).json()[0]['info']

        assert (
            [l['value'] for l in info['labels']['labels'] if l['key'] == 'offer_region'][0] == 'USA'
        )
    
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.region_awareness
@sdk_utils.dcos_ee_only
def test_nodes_can_deploy_to_remote_region():
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        3,
        additional_options={"service": {"region": "Europe"}})

    sdk_plan.wait_for_completed_deploy(config.SERVICE_NAME)

    for pod_name in POD_NAMES:
        info = sdk_cmd.service_request(
            'GET', config.SERVICE_NAME, '/v1/pod/{}/info'.format(pod_name)
        ).json()[0]['info']

        assert (
            [l['value'] for l in info['labels']['labels'] if l['key'] == 'offer_region'][0] ==
            'Europe'
        )

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.region_awareness
@sdk_utils.dcos_ee_only
def test_region_config_update_does_not_succeed():
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 3)

    sdk_plan.wait_for_completed_deploy(config.SERVICE_NAME)
    change_region_config('Europe')
    sdk_plan.wait_for_completed_deploy(config.SERVICE_NAME)

    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace hello-0')
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)

    info = sdk_cmd.service_request(
        'GET', config.SERVICE_NAME, '/v1/pod/hello-0/info'.format(pod_name)
    ).json()[0]['info']

    assert (
        [l['value'] for l in info['labels']['labels'] if l['key'] == 'offer_region'][0] == 'USA'
    )

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def change_region_config(region_name):
    config = sdk_marathon.get_config(config.SERVICE_NAME)
    config['env']['SERVICE_REGION'] = region_name
    sdk_marathon.update_app(config.SERVICE_NAME, config)
