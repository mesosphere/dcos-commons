import dcos.http
import json
import pytest
import re
import shakedown

from tests.test_utils import (
    PACKAGE_NAME,
    check_health,
    get_marathon_config,
    get_deployment_plan,
    get_sidecar_plan,
    get_task_count,
    install,
    marathon_api_url,
    request,
    run_dcos_cli_cmd,
    uninstall,
    spin,
    start_sidecar_plan
)


def setup_module(module):
    uninstall()
    options = {
        "service": {
            "spec_file": "examples/sidecar.yml"
        }
    }

    install(None, PACKAGE_NAME, options)


@pytest.mark.sanity
def test_deploy():
    deployment_plan = get_deployment_plan().json()
    print("deployment_plan: " + str(deployment_plan))

    assert(len(deployment_plan['phases']) == 2)
    assert(deployment_plan['phases'][0]['name'] == 'server-deploy')
    assert(deployment_plan['phases'][1]['name'] == 'once-deploy')
    assert(len(deployment_plan['phases'][0]['steps']) == 2)
    assert(len(deployment_plan['phases'][1]['steps']) == 2)


@pytest.mark.sanity
def test_sidecar():
    start_sidecar_plan()
    sidecar_plan = get_sidecar_plan().json()
    print("sidecar_plan: " + str(sidecar_plan))

    assert(len(sidecar_plan['phases']) == 1)
    assert(sidecar_plan['phases'][0]['name'] == 'sidecar-deploy')
    assert(len(sidecar_plan['phases'][0]['steps']) == 2)
