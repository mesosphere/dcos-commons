#!/usr/bin/python

import shakedown

# Utilities relating to package management


def get_pkg_version(package_name):
    return re.search(
        r'"version": "(\S+)"',
        run_dcos_cli_cmd('dcos package describe {}'.format(package_name))).group(1)


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
        get_pkg_version(package_name)

    def success_predicate(pkg_version):
        return pkg_version != prev_version, 'Package version has not changed'
    spin(fn, success_predicate)
