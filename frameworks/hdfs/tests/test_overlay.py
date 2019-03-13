from xml.etree import ElementTree

import pytest
import retrying

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_networks
import sdk_tasks

from tests import config


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS,
            timeout_seconds=30 * 60,
        )

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(autouse=True)
def pre_test_setup():
    config.check_healthy(service_name=config.SERVICE_NAME)


@pytest.fixture(scope="module", autouse=True)
def hdfs_client():
    try:
        client = config.get_hdfs_client_app(config.SERVICE_NAME)
        sdk_marathon.install_app(client)
        yield client

    finally:
        sdk_marathon.destroy_app(client["id"])


@pytest.mark.overlay
@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.9")
def test_tasks_on_overlay():
    tasks = sdk_tasks.check_task_count(config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)
    for task in tasks:
        sdk_networks.check_task_network(task.name)


@pytest.mark.overlay
@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.9")
def test_endpoints_on_overlay():
    endpoint_names = sdk_networks.get_endpoint_names(config.PACKAGE_NAME, config.SERVICE_NAME)
    assert set(endpoint_names) == set(["hdfs-site.xml", "core-site.xml"])
    for endpoint_name in endpoint_names:
        # Validate that XML is parseable:
        ElementTree.fromstring(sdk_networks.get_endpoint_string(config.PACKAGE_NAME, config.SERVICE_NAME, endpoint_name))


@pytest.mark.overlay
@pytest.mark.sanity
@pytest.mark.data_integrity
@pytest.mark.dcos_min_version("1.9")
def test_write_and_read_data_on_overlay(hdfs_client):
    test_filename = config.get_unique_filename("test_data_overlay")
    config.hdfs_client_write_data(test_filename)
    config.hdfs_client_read_data(test_filename)
    config.check_healthy(config.SERVICE_NAME)


@pytest.mark.data_integrity
@pytest.mark.sanity
def test_integrity_on_data_node_failure(hdfs_client):
    """
    Verifies proper data replication among data nodes.
    """
    test_filename = config.get_unique_filename("test_datanode_fail")

    # An HDFS write will only successfully return when the data replication has taken place
    config.hdfs_client_write_data(test_filename)

    # Should have 3 data nodes (data-0,1,2), kill 2 of them:
    data_tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME, "data")
    for idx in range(2):
        sdk_cmd.kill_task_with_pattern("DataNode", "nobody", agent_host=data_tasks[idx].host)

    config.hdfs_client_read_data(test_filename)

    config.check_healthy(config.SERVICE_NAME)


@pytest.mark.data_integrity
@pytest.mark.sanity
def test_integrity_on_name_node_failure(hdfs_client):
    """
    The first name node (name-0-node) is the active name node by default when HDFS gets installed.
    This test checks that it is possible to write and read data after the active name node fails
    so as to verify a failover sustains expected functionality.
    """

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=config.DEFAULT_HDFS_TIMEOUT * 1000
    )
    def _get_active_name_node():
        for candidate in ("name-0-node", "name-1-node"):
            if is_name_node_active(candidate):
                return candidate
        raise Exception("Failed to determine active name node")

    active_name_node = _get_active_name_node()
    sdk_cmd.kill_task_with_pattern(
        "NameNode",
        "nobody",
        agent_host=sdk_tasks.get_service_tasks(config.SERVICE_NAME, active_name_node)[0].host,
    )

    # After the previous active namenode was killed, the opposite namenode should marked active:
    if active_name_node == "name-1-node":
        new_active_name_node = "name-0-node"
    else:
        new_active_name_node = "name-1-node"

    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=config.DEFAULT_HDFS_TIMEOUT * 1000,
        retry_on_result=lambda res: not res,
    )
    def _wait_for_failover_to_complete(namenode):
        return is_name_node_active(namenode)

    _wait_for_failover_to_complete(new_active_name_node)

    test_filename = config.get_unique_filename("test_namenode_fail")

    config.hdfs_client_write_data(test_filename)
    config.hdfs_client_read_data(test_filename)

    config.check_healthy(config.SERVICE_NAME)


def is_name_node_active(namenode):
    success, stdout, _ = config.run_client_command(config.hadoop_command("haadmin -getServiceState {}".format(namenode)))
    return success and stdout.strip() == "active"
