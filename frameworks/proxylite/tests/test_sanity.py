import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan
import sdk_utils as utils

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.smoke
def test_install():
    pass # Setup makes sure install has completed


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
def test_httpd():
    cmd.request('get', '{}/pyhttpsd'.format(shakedown.dcos_service_url('proxylite')))


@pytest.mark.sanity
def test_plan():
    plan.wait_for_completed_deployment(PACKAGE_NAME)
