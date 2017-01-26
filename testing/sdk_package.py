'''Utilities relating to package management'''

import shakedown
import sdk_cmd
import sdk_spin

import re


def get_pkg_version(package_name):
    return re.search(
        r'"version": "(\S+)"',
        sdk_cmd.run_cli('dcos package describe {}'.format(package_name))).group(1)


def get_repo_list():
    return shakedown.get_package_repos()['repositories']


def remove_repo(repo_name, package_name, prev_version):
    assert shakedown.remove_package_repo(repo_name)
    check_default_version_available(package_name, prev_version)


def add_repo(repo_name, repo_url, package_name, prev_version):
    assert shakedown.add_package_repo(repo_name, repo_url, 0)
    # Make sure the new repo packages are available
    # The above invocation's effects don't occur immediately
    # so we spin until they do
    check_default_version_available(package_name, prev_version)


def check_default_version_available(package_name, prev_version):
    def fn():
        return get_pkg_version(package_name) != prev_version
    sdk_spin.time_wait_noisy(lambda: fn())
