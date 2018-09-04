import pytest
import sdk_install as install
import sdk_networks
import sdk_tasks
import sdk_utils
from tests import config, test_utils


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS,
        )

        yield  # let the test session execute
    finally:
        install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.overlay
@pytest.mark.smoke
@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.9")
def test_service_overlay_health():
    """Installs SDK based Kafka on with virtual networks set to True. Tests that the deployment completes
    and the service is healthy, then checks that all of the service tasks (brokers) are on the overlay network
    """
    tasks = sdk_tasks.check_task_count(config.SERVICE_NAME, config.DEFAULT_BROKER_COUNT)
    for task in tasks:
        sdk_networks.check_task_network(task.name)


@pytest.mark.smoke
@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_overlay_network_deployment_and_endpoints():
    endpoint_names = sdk_networks.get_endpoint_names(config.PACKAGE_NAME, config.SERVICE_NAME)
    assert set(["broker", "zookeeper"]) == set(endpoint_names)

    sdk_networks.check_endpoint_on_overlay(config.PACKAGE_NAME, config.SERVICE_NAME, "broker", config.DEFAULT_BROKER_COUNT)

    zookeeper = sdk_networks.get_endpoint_string(
        config.PACKAGE_NAME, config.SERVICE_NAME, "zookeeper"
    )
    assert zookeeper == "master.mesos:2181/{}".format(sdk_utils.get_zk_path(config.SERVICE_NAME))


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_pod_restart_on_overlay():
    test_utils.restart_broker_pods()
    test_overlay_network_deployment_and_endpoints()


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_pod_replace_on_overlay():
    test_utils.replace_broker_pod()
    test_overlay_network_deployment_and_endpoints()


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_topic_create_overlay():
    test_utils.create_topic(config.EPHEMERAL_TOPIC_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_topic_delete_overlay():
    test_utils.delete_topic(config.EPHEMERAL_TOPIC_NAME)
