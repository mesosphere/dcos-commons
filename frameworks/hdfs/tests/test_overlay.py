import os
import pytest
import time

from xml.etree import ElementTree
# Do not use import *; it makes it harder to determine the origin of config
# items
from tests.config import *

import sdk_install
import sdk_networks
import sdk_utils

overlay_nostrict = pytest.mark.skipif(os.environ.get("SECURITY") == "strict",
    reason="overlay tests currently broken in strict")


def setup_module(module):
    sdk_install.uninstall(PACKAGE_NAME)
    sdk_utils.gc_frameworks()
    sdk_install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT,
                    additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)
    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)


def setup_function(function):
    check_healthy()


def teardown_module(module):
    sdk_install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
@overlay_nostrict
@sdk_utils.dcos_1_9_or_higher
def test_tasks_on_overlay():
    hdfs_tasks = shakedown.shakedown.get_service_task_ids(PACKAGE_NAME)
    assert len(hdfs_tasks) == DEFAULT_TASK_COUNT, "Not enough tasks got launched,"\
        "should be {} got {}".format(len(hdfs_tasks), DEFAULT_TASK_COUNT)
    for task in hdfs_tasks:
        sdk_networks.check_task_network(task)


@pytest.mark.overlay
@pytest.mark.sanity
@overlay_nostrict
@sdk_utils.dcos_1_9_or_higher
def test_endpoints_on_overlay():
    observed_endpoints = sdk_networks.get_and_test_endpoints("", PACKAGE_NAME, 2)
    expected_endpoints = ("hdfs-site.xml", "core-site.xml")
    for endpoint in expected_endpoints:
        assert endpoint in observed_endpoints, "missing {} endpoint".format(endpoint)
        xmlout, err, rc = shakedown.run_dcos_command("{} endpoints {}".format(PACKAGE_NAME, endpoint))
        assert rc == 0, "failed to get {} endpoint. \nstdout:{},\nstderr:{} ".format(endpoint, xmlout, err)
        ElementTree.fromstring(xmlout)


@pytest.mark.overlay
@pytest.mark.sanity
@pytest.mark.data_integrity
@overlay_nostrict
@sdk_utils.dcos_1_9_or_higher
def test_write_and_read_data_on_overlay():
    # use mesos DNS here because we want the host IP to run the command
    shakedown.wait_for(
        lambda: write_data_to_hdfs("data-0-node.hdfs.mesos", TEST_FILE_1_NAME),
        timeout_seconds=DEFAULT_HDFS_TIMEOUT)

    shakedown.wait_for(
        lambda: read_data_from_hdfs("data-2-node.hdfs.mesos", TEST_FILE_1_NAME),
        timeout_seconds=DEFAULT_HDFS_TIMEOUT)

    check_healthy()
