import dcos.http
import pytest
import re
import shakedown

from tests.test_utils import (
    PACKAGE_NAME,
    check_health,
    install,
    marathon_api_url_with_param,
    request,
    run_dcos_cli_cmd,
    spin,
    uninstall,
)


MASTER_CUSTOM_NAME='Master Custom'
# TODO: replace this URL once we have a stock version from which to upgrade
MASTER_CUSTOM_URL='https://infinity-artifacts.s3.amazonaws.com/reference-latest/stub-universe-reference.zip'


def setup_module(module):
    uninstall()


def teardown_module(module):
    uninstall()


@pytest.mark.sanity
def test_upgrade():
    test_version = get_pkg_version()
    print('Found test version: {}'.format(test_version))
    add_repo(test_version)
    master_version = get_pkg_version()
    print('Found master version: {}'.format(master_version))
    print('Installing master version')
    install(master_version)
    check_health()
    print('Upgrading to test version')
    destroy_service()
    install(test_version)
    check_health()
    # clean up
    remove_repo(master_version)


def get_pkg_version():
    cmd = 'dcos package describe {}'.format(PACKAGE_NAME)
    pkg_description = run_dcos_cli_cmd(cmd)
    regex = r'"version": "(\S+)"'
    match = re.search(regex, pkg_description)
    return match.group(1)


def add_repo(prev_version):
    assert shakedown.add_package_repo(
        MASTER_CUSTOM_NAME,
        MASTER_CUSTOM_URL,
        0)
    # Make sure the new repo packages are available
    new_default_version_available(prev_version)


def new_default_version_available(prev_version):
    def fn():
        get_pkg_version()
    def success_predicate(pkg_version):
        return (pkg_version != prev_version, 'Package version has not changed')
    spin(fn, success_predicate)


def destroy_service():
    destroy_endpoint = marathon_api_url_with_param('apps', PACKAGE_NAME)
    request(dcos.http.delete, destroy_endpoint)
    # Make sure the scheduler has been destroyed
    def fn():
        shakedown.get_service(PACKAGE_NAME)

    def success_predicate(service):
        return (service == None, 'Service not destroyed')

    spin(fn, success_predicate)


def remove_repo(prev_version):
    assert shakedown.remove_package_repo(MASTER_CUSTOM_NAME)
    new_default_version_available(prev_version)