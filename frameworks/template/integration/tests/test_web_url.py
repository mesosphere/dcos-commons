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
            "spec_file": "examples/web-url.yml"
        }
    }

    install(None, PACKAGE_NAME, options)


@pytest.mark.sanity
def test_deploy():
    deployment_plan = get_deployment_plan().json()
    print("deployment_plan: " + str(deployment_plan))

    assert(len(deployment_plan['phases']) == 1)
    assert(deployment_plan['phases'][0]['name'] == 'template')
    assert(len(deployment_plan['phases'][0]['steps']) == 1)

