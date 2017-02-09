import dcos.http
import pytest
import re
import shakedown
import time

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_package as package
import sdk_tasks as tasks

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

from tests.test_data_integrity import (
    write_some_data,
    read_some_data
)

MASTER_CUSTOM_NAME='Master Custom'
# TODO: replace this URL once we have a stable, released version from which to upgrade
MASTER_CUSTOM_URL='https://infinity-artifacts.s3.amazonaws.com/hdfs-placeholder/hdfs/' + \
                  '20170104-112950-munTPdy9HkuhvIEC/stub-universe-hdfs.zip'
TEST_FILE_NAME = "upgrade_test"


def setup_module(module):
    install.uninstall(PACKAGE_NAME)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.skip(reason="Waiting for released version from which to upgrade")
@pytest.mark.upgrade
@pytest.mark.sanity
def test_upgrade_downgrade():
    # Ensure both Universe and the test repo exist.
    if len(package.get_repo_list()) != 2:
        print('No test repo found.  Skipping test_upgrade_downgrade')
        return

    test_version = package.get_pkg_version()
    print('Found test version: {}'.format(test_version))

    package.add_repo(MASTER_CUSTOM_NAME, MASTER_CUSTOM_URL, prev_version=test_version)

    master_version = package.get_pkg_version()
    print('Found master version: {}'.format(master_version))
    print('Installing master version')
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, package_version=master_version)
    write_some_data("data-0-node.hdfs.mesos", TEST_FILE_NAME)
    # gives chance for write to succeed and replication to occur
    time.sleep(5)


    print('Upgrading to test version')
    marathon.destroy_app(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, package_version=test_version)
    read_some_data("data-0-node.hdfs.mesos", TEST_FILE_NAME)

    print('Downgrading to master version')
    marathon.destroy_app(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, package_version=master_version)
    read_some_data("data-0-node.hdfs.mesos", TEST_FILE_NAME)

    # clean up
    package.remove_repo(MASTER_CUSTOM_NAME, PACKAGE_NAME, master_version)
