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


def setup_module(module):
    uninstall()


def teardown_module(module):
    uninstall()


@pytest.mark.sanity
@pytest.mark.upgrade
def test_upgrade_downgrade():
    # Ensure both Universe and the test repo exist.
    # In particular, the Framework Test Suite only runs packages from Universe;
    # it doesn't add a test repo like the PR jobs.
    if len(shakedown.get_package_repos()['repositories']) != 2:
        print('No test repo found.  Skipping test_upgrade_downgrade')
        return

    test_repo_name, test_repo_url = get_test_repo_info()
    test_version = get_pkg_version()
    print('Found test version: {}'.format(test_version))
    remove_repo(test_repo_name, test_version)
    master_version = get_pkg_version()
    print('Found master version: {}'.format(master_version))

    print('Installing master version')
    install(master_version)
    check_health()

    print('Upgrading to test version')
    destroy_service()
    add_repo(test_repo_name, test_repo_url, prev_version=master_version)
    install(test_version)
    check_health()

    print('Downgrading to master version')
    destroy_service()
    install(master_version)
    check_health()


def get_test_repo_info():
    repos = shakedown.get_package_repos()
    test_repo = repos['repositories'][0]
    return test_repo['name'], test_repo['uri']


def get_pkg_version():
    pkg_description = run_dcos_cli_cmd('package describe {}'.format(PACKAGE_NAME))
    regex = r'"version": "(\S+)"'
    match = re.search(regex, pkg_description)
    return match.group(1)


def add_repo(repo_name, repo_url, prev_version):
    assert shakedown.add_package_repo(
        repo_name,
        repo_url,
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


def remove_repo(repo_name, prev_version):
    assert shakedown.remove_package_repo(repo_name)
    new_default_version_available(prev_version)
