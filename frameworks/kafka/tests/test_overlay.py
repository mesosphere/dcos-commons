import pytest
import sdk_cmd
import sdk_install as install
import sdk_networks
import sdk_tasks
import sdk_utils
import shakedown
from tests import config, test_utils


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)

        yield  # let the test session execute
    finally:
        install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.overlay
@pytest.mark.smoke
@pytest.mark.sanity
@pytest.mark.dcos_min_version('1.9')
def test_service_overlay_health():
    """Installs SDK based Kafka on with virtual networks set to True. Tests that the deployment completes
    and the service is healthy, then checks that all of the service tasks (brokers) are on the overlay network
    """
    shakedown.service_healthy(config.SERVICE_NAME)
    broker_tasks = (
        "kafka-0-broker",
        "kafka-1-broker",
        "kafka-2-broker"
    )
    for task in broker_tasks:
        sdk_networks.check_task_network(task)


@pytest.mark.smoke
@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version('1.9')
def test_overlay_network_deployment_and_endpoints():
    # double check
    sdk_tasks.check_running(config.SERVICE_NAME, config.DEFAULT_BROKER_COUNT)
    endpoints = sdk_networks.get_and_test_endpoints(config.PACKAGE_NAME, config.SERVICE_NAME, "", 2)
    assert "broker" in endpoints, "broker is missing from endpoints {}".format(endpoints)
    assert "zookeeper" in endpoints, "zookeeper missing from endpoints {}".format(endpoints)
    broker_endpoints = sdk_networks.get_and_test_endpoints(config.PACKAGE_NAME, config.SERVICE_NAME, "broker", 3)
    sdk_networks.check_endpoints_on_overlay(broker_endpoints)

    zookeeper = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'endpoints zookeeper')
    assert zookeeper.rstrip() == 'master.mesos:2181/{}'.format(sdk_utils.get_zk_path(config.SERVICE_NAME))


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version('1.9')
def test_pod_restart_on_overlay():
    test_utils.restart_broker_pods()
    test_overlay_network_deployment_and_endpoints()


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version('1.9')
def test_pod_replace_on_overlay():
    test_utils.replace_broker_pod()
    test_overlay_network_deployment_and_endpoints()


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version('1.9')
def test_topic_create_overlay():
    test_utils.create_topic(config.EPHEMERAL_TOPIC_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version('1.9')
def test_topic_delete_overlay():
    test_utils.delete_topic(config.EPHEMERAL_TOPIC_NAME)
