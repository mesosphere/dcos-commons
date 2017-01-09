import dcos.http
import pytest
import re
import shakedown
import time

from tests.test_data_integrity import (
    write_some_data,
    read_some_data
)

from tests.test_utils import (
    PACKAGE_NAME,
    check_health,
    install,
    marathon_api_url_with_param,
    request,
    run_dcos_cli_cmd,
    spin,
    uninstall
)


MASTER_CUSTOM_NAME='Master Custom'
# TODO: replace this URL once we have a stable, released version from which to upgrade
MASTER_CUSTOM_URL='https://infinity-artifacts.s3.amazonaws.com/hdfs-placeholder/hdfs/' + \
                  '20170104-112950-munTPdy9HkuhvIEC/stub-universe-hdfs.zip'
TEST_FILE_NAME = "upgrade_test"


def setup_module(module):
    uninstall()


@pytest.mark.skip(reason="Waiting for released version from which to upgrade")
@pytest.mark.upgrade
@pytest.mark.sanity
def test_upgrade_downgrade():
    # Ensure both Universe and the test repo exist.
    if len(shakedown.get_package_repos()['repositories']) != 2:
        print('No test repo found.  Skipping test_upgrade_downgrade')
        return

    test_version = get_pkg_version()
    print('Found test version: {}'.format(test_version))

    add_repo(MASTER_CUSTOM_NAME, MASTER_CUSTOM_URL, prev_version=test_version)

    master_version = get_pkg_version()
    print('Found master version: {}'.format(master_version))
    print('Installing master version')
    install({'package_version': master_version})
    check_health()
    write_some_data("data-0-node.hdfs.mesos", TEST_FILE_NAME)
    # gives chance for write to succeed and replication to occur
    time.sleep(5)


    print('Upgrading to test version')
    destroy_and_install(test_version)
    check_health_after_version_change()

    print('Downgrading to master version')
    destroy_and_install(master_version)
    check_health_after_version_change()

    # clean up
    remove_repo(prev_version=master_version)


def remove_repo(prev_version):
    assert shakedown.remove_package_repo(MASTER_CUSTOM_NAME)
    new_default_version_available(prev_version)


def get_pkg_version():
    cmd = 'dcos package describe {}'.format(PACKAGE_NAME)
    pkg_description = run_dcos_cli_cmd(cmd)
    regex = r'"version": "(\S+)"'
    match = re.search(regex, pkg_description)
    return match.group(1)


def add_repo(repo_name, repo_url, prev_version):
    assert shakedown.add_package_repo(
        repo_name,
        repo_url,
        0)
    # Make sure the new repo packages are available
    # The above invocation's effects don't occur immediately
    # so we spin until they do
    new_default_version_available(prev_version)


def new_default_version_available(prev_version):
    def fn():
        get_pkg_version()

    def success_predicate(pkg_version):
        return pkg_version != prev_version, 'Package version has not changed'
    spin(fn, success_predicate)


def destroy_and_install(version):
    destroy_service()
    install({'package_version': version})


def destroy_service():
    destroy_endpoint = marathon_api_url_with_param('apps', PACKAGE_NAME)
    request(dcos.http.delete, destroy_endpoint)
    # Make sure the scheduler has been destroyed

    def fn():
        shakedown.get_service(PACKAGE_NAME)

    def success_predicate(service):
        return service is None, 'Service not destroyed'

    spin(fn, success_predicate)


def check_health_after_version_change():
    check_health
    read_some_data("data-0-node.hdfs.mesos", TEST_FILE_NAME)


def remove_repo(prev_version):
    assert shakedown.remove_package_repo(MASTER_CUSTOM_NAME)
    new_default_version_available(prev_version)
