import dcos.http
import json
import pytest
import re
import shakedown

PACKAGE_NAME = 'hello-world'


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
    assert(deployment_plan['phases'][0]['name'] == 'hello')
    assert(len(deployment_plan['phases'][0]['steps']) == 1)

