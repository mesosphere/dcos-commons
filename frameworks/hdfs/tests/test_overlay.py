from xml.etree import ElementTree

import pytest
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_networks
import sdk_tasks
import sdk_utils
import shakedown
from tests import config


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS,
            timeout_seconds=30*60)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(autouse=True)
def pre_test_setup():
    config.check_healthy(service_name=config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version('1.9')
def test_tasks_on_overlay():
    hdfs_tasks = shakedown.shakedown.get_service_task_ids(config.SERVICE_NAME)
    assert len(hdfs_tasks) == config.DEFAULT_TASK_COUNT, "Not enough tasks got launched,"\
        "should be {} got {}".format(len(hdfs_tasks), config.DEFAULT_TASK_COUNT)
    for task in hdfs_tasks:
        sdk_networks.check_task_network(task)


@pytest.mark.overlay
@pytest.mark.sanity
@pytest.mark.dcos_min_version('1.9')
def test_endpoints_on_overlay():
    observed_endpoints = sdk_networks.get_and_test_endpoints(config.PACKAGE_NAME, config.SERVICE_NAME, "", 2)
    expected_endpoints = ("hdfs-site.xml", "core-site.xml")
    for endpoint in expected_endpoints:
        assert endpoint in observed_endpoints, "missing {} endpoint".format(endpoint)
        xmlout = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'endpoints {}'.format(endpoint))
        ElementTree.fromstring(xmlout)


@pytest.mark.overlay
@pytest.mark.sanity
@pytest.mark.data_integrity
@pytest.mark.dcos_min_version('1.9')
def test_write_and_read_data_on_overlay():
    config.write_data_to_hdfs(config.SERVICE_NAME, config.TEST_FILE_1_NAME)
    config.read_data_from_hdfs(config.SERVICE_NAME, config.TEST_FILE_1_NAME)
    config.check_healthy(service_name=config.SERVICE_NAME)


@pytest.mark.data_integrity
@pytest.mark.sanity
def test_integrity_on_data_node_failure():
    """
    Verifies proper data replication among data nodes.
    """
    # An HDFS write will only successfully return when the data replication has taken place
    config.write_data_to_hdfs(config.SERVICE_NAME, config.TEST_FILE_1_NAME)

    sdk_tasks.kill_task_with_pattern("DataNode", sdk_hosts.system_host(config.SERVICE_NAME, 'data-0-node'))
    sdk_tasks.kill_task_with_pattern("DataNode", sdk_hosts.system_host(config.SERVICE_NAME, 'data-1-node'))

    config.read_data_from_hdfs(config.SERVICE_NAME, config.TEST_FILE_1_NAME)

    config.check_healthy(service_name=config.SERVICE_NAME)


@pytest.mark.data_integrity
@pytest.mark.sanity
def test_integrity_on_name_node_failure():
    """
    The first name node (name-0-node) is the active name node by default when HDFS gets installed.
    This test checks that it is possible to write and read data after the active name node fails
    so as to verify a failover sustains expected functionality.
    """
    active_name_node = config.get_active_name_node(config.SERVICE_NAME)
    sdk_tasks.kill_task_with_pattern("NameNode", sdk_hosts.system_host(config.SERVICE_NAME, active_name_node))

    predicted_active_name_node = "name-1-node"
    if active_name_node == "name-1-node":
        predicted_active_name_node = "name-0-node"

    wait_for_failover_to_complete(predicted_active_name_node)

    config.write_data_to_hdfs(config.SERVICE_NAME, config.TEST_FILE_2_NAME)
    config.read_data_from_hdfs(config.SERVICE_NAME, config.TEST_FILE_2_NAME)

    config.check_healthy(service_name=config.SERVICE_NAME)


def wait_for_failover_to_complete(namenode):
    """
    Inspects the name node logs to make sure ZK signals a complete failover.
    The given namenode is the one to become active after the failover is complete.
    """
    def failover_detection():
        status = config.get_name_node_status(config.SERVICE_NAME, namenode)
        return status == "active"

    shakedown.wait_for(lambda: failover_detection(), timeout_seconds=config.DEFAULT_HDFS_TIMEOUT)
