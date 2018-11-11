import json
import pytest

import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_metrics
import sdk_networks
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils

from tests import config, test_utils, client


FOLDERED_NAME = sdk_utils.get_foldered_name(config.SERVICE_NAME)


@pytest.fixture(scope="module")
def kafka_client():
    try:
        kafka_client = client.KafkaClient("kafka-client", config.PACKAGE_NAME, FOLDERED_NAME)
        kafka_client.install()
        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security, kafka_client: client.KafkaClient):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, FOLDERED_NAME)

        sdk_upgrade.test_upgrade(
            config.PACKAGE_NAME,
            FOLDERED_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options={"service": {"name": FOLDERED_NAME}, "brokers": {"cpus": 0.5}},
        )

        # wait for brokers to finish registering before starting tests
        kafka_client.connect(config.DEFAULT_BROKER_COUNT)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, FOLDERED_NAME)


# --------- Endpoints -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_address():
    endpoints = sdk_networks.get_endpoint(config.PACKAGE_NAME, FOLDERED_NAME, "broker")

    # NOTE: do NOT closed-to-extension assert len(endpoints) == _something_
    assert len(endpoints["address"]) == config.DEFAULT_BROKER_COUNT
    assert len(endpoints["dns"]) == config.DEFAULT_BROKER_COUNT
    for i in range(len(endpoints["dns"])):
        assert (
            sdk_hosts.autoip_host(FOLDERED_NAME, "kafka-{}-broker".format(i)) in endpoints["dns"][i]
        )
    assert endpoints["vip"] == sdk_hosts.vip_host(FOLDERED_NAME, "broker", 9092)


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_zookeeper_default():
    zookeeper = sdk_networks.get_endpoint_string(config.PACKAGE_NAME, FOLDERED_NAME, "zookeeper")
    assert zookeeper == "master.mesos:2181/{}".format(sdk_utils.get_zk_path(FOLDERED_NAME))


@pytest.mark.smoke
@pytest.mark.sanity
def test_custom_zookeeper(kafka_client: client.KafkaClient):
    broker_ids = sdk_tasks.get_task_ids(FOLDERED_NAME, "{}-".format(config.DEFAULT_POD_TYPE))

    # create a topic against the default zk:
    kafka_client.create_topic(config.DEFAULT_TOPIC_NAME)

    marathon_config = sdk_marathon.get_config(FOLDERED_NAME)
    # should be using default path when this envvar is empty/unset:
    assert marathon_config["env"]["KAFKA_ZOOKEEPER_URI"] == ""

    # use a custom zk path that's WITHIN the 'dcos-service-' path, so that it's automatically cleaned up in uninstall:
    zk_path = "master.mesos:2181/{}/CUSTOMPATH".format(sdk_utils.get_zk_path(FOLDERED_NAME))
    marathon_config["env"]["KAFKA_ZOOKEEPER_URI"] = zk_path
    sdk_marathon.update_app(marathon_config)

    sdk_tasks.check_tasks_updated(FOLDERED_NAME, "{}-".format(config.DEFAULT_POD_TYPE), broker_ids)
    sdk_plan.wait_for_completed_deployment(FOLDERED_NAME)

    # wait for brokers to finish registering
    kafka_client.check_broker_count(config.DEFAULT_BROKER_COUNT)

    zookeeper = sdk_networks.get_endpoint_string(config.PACKAGE_NAME, FOLDERED_NAME, "zookeeper")
    assert zookeeper == zk_path

    # topic created earlier against default zk should no longer be present:
    rc, stdout, _ = sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_NAME, "topic list")
    assert rc == 0, "Topic list command failed"

    assert config.DEFAULT_TOPIC_NAME not in json.loads(stdout)

    # tests from here continue with the custom ZK path...


# --------- Broker -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_broker_list():
    rc, stdout, _ = sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_NAME, "broker list")
    assert rc == 0, "Broker list command failed"
    assert set(json.loads(stdout)) == set([str(i) for i in range(config.DEFAULT_BROKER_COUNT)])


@pytest.mark.smoke
@pytest.mark.sanity
def test_broker_invalid():
    rc, stdout, stderr = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_NAME, "broker get {}".format(config.DEFAULT_BROKER_COUNT + 1)
    )
    assert rc != 0, "Invalid broker id should have failed"
    assert "error" in stderr


# --------- Pods -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_pods_restart():
    test_utils.restart_broker_pods(
        config.PACKAGE_NAME, FOLDERED_NAME, config.DEFAULT_POD_TYPE, config.DEFAULT_BROKER_COUNT
    )


@pytest.mark.smoke
@pytest.mark.sanity
def test_pod_replace(kafka_client: client.KafkaClient):
    test_utils.replace_broker_pod(
        config.PACKAGE_NAME, FOLDERED_NAME, config.DEFAULT_POD_TYPE, config.DEFAULT_BROKER_COUNT
    )
    kafka_client.connect(config.DEFAULT_BROKER_COUNT)


# --------- CLI -------------


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.9")
def test_metrics():
    expected_metrics = [
        "kafka.network.RequestMetrics.ResponseQueueTimeMs.max",
        "kafka.socket-server-metrics.io-ratio",
        "kafka.controller.ControllerStats.LeaderElectionRateAndTimeMs.p95",
    ]

    def expected_metrics_exist(emitted_metrics):
        return sdk_metrics.check_metrics_presence(emitted_metrics, expected_metrics)

    sdk_metrics.wait_for_service_metrics(
        config.PACKAGE_NAME,
        FOLDERED_NAME,
        "kafka-0",
        "kafka-0-broker",
        config.DEFAULT_KAFKA_TIMEOUT,
        expected_metrics_exist,
    )
