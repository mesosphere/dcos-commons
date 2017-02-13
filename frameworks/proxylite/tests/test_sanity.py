import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


@pytest.mark.sanity
def test_example():
    cmd.request('get', '{}/example'.format(shakedown.dcos_service_url('proxylite')))


@pytest.mark.sanity
def test_google():
    cmd.request('get', '{}/google'.format(shakedown.dcos_service_url('proxylite')))


@pytest.mark.sanity
def test_httpd():
    cmd.request('get', '{}/httpd'.format(shakedown.dcos_service_url('proxylite')))


@pytest.mark.sanity
def test_plan():
    plan.get_deployment_plan(PACKAGE_NAME)
