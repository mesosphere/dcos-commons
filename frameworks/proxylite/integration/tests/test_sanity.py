import dcos.http
import dcos.marathon
import json
import pytest
import re
import shakedown

from tests.test_utils import (
    PACKAGE_NAME,
    check_health,
    get_marathon_config,
    get_deployment_plan,
    install,
    marathon_api_url,
    request,
    run_dcos_cli_cmd,
    uninstall,
    spin
)


def setup_module(module):
    uninstall()
    install()
    check_health()


@pytest.mark.sanity
def test_httpd_proxy():
    request(
        dcos.http.get,
        '{}/httpd'.format(shakedown.dcos_service_url('proxylite')),
        json=config)

