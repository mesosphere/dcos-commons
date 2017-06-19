import time
import pytest

from xml.etree import ElementTree
from tests.config import *

import sdk_install as install
import sdk_networks as networks


OVERLAY_OPTIONS = {'service':{'virtual_network':True}}


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT,
                    additional_options=OVERLAY_OPTIONS)


def setup_function(function):
    check_healthy()


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
def test_tasks_on_overlay():
    hdfs_tasks = shakedown.shakedown.get_service_task_ids(PACKAGE_NAME)
    assert len(hdfs_tasks) == DEFAULT_TASK_COUNT, "Not enough tasks got launched,"\
        "should be {} got {}".format(len(hdfs_tasks), DEFAULT_TASK_COUNT)
    for task in hdfs_tasks:
        networks.check_task_network(task)


@pytest.mark.overlay
@pytest.mark.sanity
def test_endpoints_on_overlay():
    observed_endpoints = networks.get_and_test_endpoints("", PACKAGE_NAME, 2)
    expected_endpoints = ("hdfs-site.xml", "core-site.xml")
    for endpoint in expected_endpoints:
        assert endpoint in observed_endpoints, "missing {} endpoint".format(endpoint)
        xmlout, err, rc = shakedown.run_dcos_command("{} endpoints {}".format(PACKAGE_NAME, endpoint))
        assert rc == 0, "failed to get {} endpoint. \nstdout:{},\nstderr:{} ".format(endpoint, xmlout, err)
        ElementTree.fromstring(xmlout)


@pytest.mark.overlay
@pytest.mark.sanity
@pytest.mark.skip("HDFS-451, not working on jenkins")
def test_read_and_write_data_on_overlay():
    # use mesos DNS here because we want the host IP to run the command
    shakedown.wait_for(
        lambda: write_data_to_hdfs("data-0-node.hdfs.mesos", TEST_FILE_1_NAME),
        timeout_seconds=HDFS_CMD_TIMEOUT_SEC)

    # gives chance for write to succeed and replication to occur
    time.sleep(9)

    shakedown.wait_for(
        lambda: read_data_from_hdfs("data-2-node.hdfs.mesos", TEST_FILE_1_NAME),
        timeout_seconds=HDFS_CMD_TIMEOUT_SEC)

    check_healthy()
