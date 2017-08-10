import pytest

from xml.etree import ElementTree
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    TEST_FILE_1_NAME,
    TEST_FILE_2_NAME,
    check_healthy,
    get_name_node_status,
    write_data_to_hdfs,
    read_data_from_hdfs,
    get_active_name_node
)

import sdk_hosts
import sdk_install
import sdk_networks
import sdk_utils
import sdk_plan
import sdk_tasks

import shakedown

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME)
        sdk_install.install(
            PACKAGE_NAME,
            DEFAULT_TASK_COUNT,
            additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS,
            timeout_seconds=30*60)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME)


@pytest.fixture(autouse=True)
def pre_test_setup():
    check_healthy(service_name=PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
@sdk_utils.dcos_1_9_or_higher
def test_tasks_on_overlay():
    hdfs_tasks = shakedown.shakedown.get_service_task_ids(PACKAGE_NAME)
    assert len(hdfs_tasks) == DEFAULT_TASK_COUNT, "Not enough tasks got launched,"\
        "should be {} got {}".format(len(hdfs_tasks), DEFAULT_TASK_COUNT)
    for task in hdfs_tasks:
        sdk_networks.check_task_network(task)


@pytest.mark.overlay
@pytest.mark.sanity
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
@sdk_utils.dcos_1_9_or_higher
def test_write_and_read_data_on_overlay():
    write_data_to_hdfs(PACKAGE_NAME, TEST_FILE_1_NAME)
    read_data_from_hdfs(PACKAGE_NAME, TEST_FILE_1_NAME)
    check_healthy(service_name=PACKAGE_NAME)


@pytest.mark.data_integrity
@pytest.mark.sanity
def test_integrity_on_data_node_failure():
    """
    Verifies proper data replication among data nodes.
    """
    # An HDFS write will only successfully return when the data replication has taken place
    write_data_to_hdfs(PACKAGE_NAME, TEST_FILE_1_NAME)

    sdk_tasks.kill_task_with_pattern("DataNode", sdk_hosts.system_host(PACKAGE_NAME, 'data-0-node'))
    sdk_tasks.kill_task_with_pattern("DataNode", sdk_hosts.system_host(PACKAGE_NAME, 'data-1-node'))

    read_data_from_hdfs(PACKAGE_NAME, TEST_FILE_1_NAME)

    check_healthy(service_name=PACKAGE_NAME)


@pytest.mark.data_integrity
@pytest.mark.sanity
def test_integrity_on_name_node_failure():
    """
    The first name node (name-0-node) is the active name node by default when HDFS gets installed.
    This test checks that it is possible to write and read data after the active name node fails
    so as to verify a failover sustains expected functionality.
    """
    active_name_node = get_active_name_node(PACKAGE_NAME)
    sdk_tasks.kill_task_with_pattern("NameNode", sdk_hosts.system_host(PACKAGE_NAME, active_name_node))

    predicted_active_name_node = "name-1-node"
    if active_name_node == "name-1-node":
        predicted_active_name_node = "name-0-node"

    wait_for_failover_to_complete(predicted_active_name_node)

    write_data_to_hdfs(PACKAGE_NAME, TEST_FILE_2_NAME)
    read_data_from_hdfs(PACKAGE_NAME, TEST_FILE_2_NAME)

    check_healthy(service_name=PACKAGE_NAME)


def wait_for_failover_to_complete(namenode):
    """
    Inspects the name node logs to make sure ZK signals a complete failover.
    The given namenode is the one to become active after the failover is complete.
    """
    def failover_detection():
        status = get_name_node_status(PACKAGE_NAME, namenode)
        return status == "active"

    shakedown.wait_for(lambda: failover_detection(), timeout_seconds=DEFAULT_HDFS_TIMEOUT)
