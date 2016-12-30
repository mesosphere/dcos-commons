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
            "spec_file": "sidecar.yml"
        }
    }

    install(None, PACKAGE_NAME, options)


@pytest.mark.sanity
def test_deploy():
    get_deployment_plan()

@pytest.mark.sanity
def test_sidecar():
    start_sidecar_plan()
    get_sidecar_plan()
