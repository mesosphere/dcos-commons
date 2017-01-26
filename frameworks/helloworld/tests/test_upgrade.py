import pytest
import re
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_spin as spin

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


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
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, package_version=master_version)

    print('Upgrading to test version')
    marathon.destroy_app(PACKAGE_NAME)
    add_repo(test_repo_name, test_repo_url, prev_version=master_version)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, package_version=test_version)

    print('Downgrading to master version')
    marathon.destroy_app(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, package_version=master_version)


def get_test_repo_info():
    repos = shakedown.get_package_repos()
    test_repo = repos['repositories'][0]
    return test_repo['name'], test_repo['uri']


def get_pkg_version():
    pkg_description = cmd.run_cli('package describe {}'.format(PACKAGE_NAME))
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
    spin.time_wait_noisy(lambda: get_pkg_version() != prev_version)


def remove_repo(repo_name, prev_version):
    assert shakedown.remove_package_repo(repo_name)
    new_default_version_available(prev_version)
