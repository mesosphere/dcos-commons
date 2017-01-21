import dcos.http
import pytest
import re
import shakedown
import time

from tests.test_data_integrity import (
    write_some_data,
    read_some_data
)

PACKAGE_NAME = 'hdfs'

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
    if len(get_repo_list()) != 2:
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
    destroy_marathon_app(PACKAGE_NAME)
    install({'package_version': test_version})
    check_health()
    read_some_data("data-0-node.hdfs.mesos", TEST_FILE_NAME)

    print('Downgrading to master version')
    destroy_marathon_app(PACKAGE_NAME)
    install({'package_version': master_version})
    check_health()
    read_some_data("data-0-node.hdfs.mesos", TEST_FILE_NAME)

    # clean up
    remove_repo(MASTER_CUSTOM_NAME, PACKAGE_NAME, master_version)
