import pytest

import sdk_install as install
import sdk_tasks as tasks
import sdk_plan as plan
import sdk_networks as networks
import sdk_utils as utils


from tests.test_utils import  *


def setup_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)
    utils.gc_frameworks()

    install.install(
        PACKAGE_NAME,
        DEFAULT_BROKER_COUNT,
        service_name=SERVICE_NAME,
        additional_options = {'service':{'virtual_network':True}})
    plan.wait_for_completed_deployment(PACKAGE_NAME)


def teardown_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)


@pytest.mark.overlay
@pytest.mark.smoke
@pytest.mark.sanity
def test_service_overlay_health():
    """Installs SDK based Kafka on with virtual networks set to True. Tests that the deployment completes
    and the service is healthy, then checks that all of the service tasks (brokers) are on the overlay network
    """
    shakedown.service_healthy(PACKAGE_NAME)
    broker_tasks = (
        "kafka-0-broker",
        "kafka-1-broker",
        "kafka-2-broker"
    )
    for task in broker_tasks:
        networks.check_task_network(task)


@pytest.mark.smoke
@pytest.mark.sanity
@pytest.mark.overlay
def test_overlay_network_deployment_and_endpoints():
    # double check
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)
    endpoints = networks.get_and_test_endpoints("", PACKAGE_NAME, 2)
    assert "broker" in endpoints, "broker is missing from endpoints {}".format(endpoints)
    assert "zookeeper" in endpoints, "zookeeper missing from endpoints {}".format(endpoints)
    broker_endpoints = networks.get_and_test_endpoints("broker", PACKAGE_NAME, 4)
    networks.check_endpoints_on_overlay(broker_endpoints)

    zookeeper = service_cli('endpoints zookeeper', get_json=False)
    assert zookeeper.rstrip() == 'master.mesos:2181/dcos-service-{}'.format(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
def test_pods_restart_on_overlay():
    restart_broker_pods()
    test_overlay_network_deployment_and_endpoints()


@pytest.mark.sanity
@pytest.mark.overlay
def test_pods_replace_on_overlay():
    replace_broker_pod()
    test_overlay_network_deployment_and_endpoints()


@pytest.mark.sanity
@pytest.mark.overlay
def test_topic_create_overlay():
    create_topic()


@pytest.mark.sanity
@pytest.mark.overlay
def test_topic_delete_overlay():
    delete_topic()
