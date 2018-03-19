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


def with_service_installation(additional_options):
    def decorator(fn):
        def wrapper(*args, **kwargs):
            try:
                sdk_install.install(
                    config.PACKAGE_NAME,
                    config.SERVICE_NAME,
                    3,
                    additional_options=additional_options)
                sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

                fn(*args, **kwargs)
            finally:
                sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        return wrapper
    return decorator


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.region_awareness
@sdk_utils.dcos_ee_only
@with_service_installation({"service": {"scenario": "MULTI_REGION"}})
def test_nodes_deploy_to_local_region_by_default():
    for pod_name in POD_NAMES:
        info = sdk_cmd.service_request(
            'GET', config.SERVICE_NAME, '/v1/pod/{}/info'.format(pod_name)
        ).json()[0]['info']

        assert (
            [l['value'] for l in info['labels']['labels'] if l['key'] == 'offer_region'][0] == 'USA'
        )


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.region_awareness
@sdk_utils.dcos_ee_only
@with_service_installation({"service": {"scenario": "MULTI_REGION", "region": "Europe"}})
def test_nodes_can_deploy_to_remote_region():
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    for pod_name in POD_NAMES:
        info = sdk_cmd.service_request(
            'GET', config.SERVICE_NAME, '/v1/pod/{}/info'.format(pod_name)
        ).json()[0]['info']

        assert (
            [l['value'] for l in info['labels']['labels'] if l['key'] == 'offer_region'][0] ==
            'Europe'
        )


@pytest.mark.dcos_min_version('1.11')
@pytest.mark.region_awareness
@sdk_utils.dcos_ee_only
@with_service_installation({"service": {"scenario": "MULTI_REGION"}})
def test_region_config_update_does_not_succeed():
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    change_region_config('Europe')
    plan = sdk_plan.get_deployment_plan(config.SERVICE_NAME)
    assert plan.get('errors', [])

    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace hello-0')
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)

    info = sdk_cmd.service_request(
        'GET', config.SERVICE_NAME, '/v1/pod/hello-0/info'
    ).json()[0]['info']

    assert (
        [l['value'] for l in info['labels']['labels'] if l['key'] == 'offer_region'][0] == 'USA'
    )


def change_region_config(region_name):
    service_config = sdk_marathon.get_config(config.SERVICE_NAME)
    service_config['env']['SERVICE_REGION'] = region_name
    sdk_marathon.update_app(config.SERVICE_NAME, service_config, wait_for_completed_deployment=False)
