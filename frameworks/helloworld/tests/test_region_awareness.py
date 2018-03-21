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
LOCAL_REGION = 'USA'
REMOTE_REGION = 'Europe'


@pytest.fixture
def local_service():
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            3,
            additional_options={
                "service": {
                    "scenario": "MULTI_REGION",
                    "allow_region_awareness": True
                }
            })

        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture
def remote_service():
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            3,
            additional_options={
                "service": {
                    "scenario": "MULTI_REGION", 
                    "allow_region_awareness": "true",
                    "region": REMOTE_REGION
                }
            })

        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.region_awareness
@sdk_utils.dcos_ee_only
def test_nodes_deploy_to_local_region_by_default(local_service):
    for pod_name in POD_NAMES:
        pod_region = get_pod_region(config.SERVICE_NAME, pod_name)

        assert pod_region == LOCAL_REGION


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.region_awareness
@sdk_utils.dcos_ee_only
def test_nodes_can_deploy_to_remote_region(remote_service):
    for pod_name in POD_NAMES:
        pod_region = get_pod_region(config.SERVICE_NAME, pod_name)

        assert pod_region == REMOTE_REGION


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.region_awareness
@sdk_utils.dcos_ee_only
def test_region_config_update_does_not_succeed(local_service):
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    change_region_config(REMOTE_REGION)
    plan = sdk_plan.get_deployment_plan(config.SERVICE_NAME)
    assert plan.get('errors', [])

    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace hello-0')
    sdk_plan.wait_for_in_progress_recovery(config.SERVICE_NAME)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)

    pod_region = get_pod_region(config.SERVICE_NAME, 'hello-0')

    assert pod_region == LOCAL_REGION


def change_region_config(region_name):
    service_config = sdk_marathon.get_config(config.SERVICE_NAME)
    service_config['env']['SERVICE_REGION'] = region_name
    sdk_marathon.update_app(config.SERVICE_NAME, service_config, wait_for_completed_deployment=False)


def get_pod_region(service_name, pod_name):
    info = sdk_cmd.service_request(
        'GET', service_name, '/v1/pod/{}/info'.format(pod_name)
    ).json()[0]['info']

    return [l['value'] for l in info['labels']['labels'] if l['key'] == 'offer_region'][0]
