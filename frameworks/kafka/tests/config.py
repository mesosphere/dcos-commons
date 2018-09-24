from toolz import merge as merge_dictionaries

import sdk_install
import sdk_utils

# allow overriding these names via envvars, for confluent tests:
PACKAGE_NAME = sdk_utils.get_package_name("kafka")
SERVICE_NAME = sdk_utils.get_service_name(PACKAGE_NAME.lstrip("beta-"))

ZOOKEEPER_PACKAGE_NAME = "kafka-zookeeper"
ZOOKEEPER_SERVICE_NAME = "kafka-zookeeper"
ZOOKEEPER_TASK_COUNT = 6

DEFAULT_BROKER_COUNT = 3
DEFAULT_TASK_COUNT = DEFAULT_BROKER_COUNT
DEFAULT_PARTITION_COUNT = 1
DEFAULT_REPLICATION_FACTOR = 1
DEFAULT_POD_TYPE = "kafka"
DEFAULT_KAFKA_TIMEOUT = 10 * 60
DEFAULT_TOPIC_NAME = "topic1"

EPHEMERAL_TOPIC_NAME = "topic_2"


def install(
    package_name,
    service_name,
    expected_running_tasks,
    additional_options={},
    package_version=None,
    timeout_seconds=25 * 60,
    wait_for_deployment=True,
):

    test_options = {"brokers": {"cpus": 0.5}}

    sdk_install.install(
        package_name=package_name,
        expected_running_tasks=expected_running_tasks,
        service_name=service_name,
        additional_options=merge_dictionaries(test_options, additional_options),
        package_version=package_version,
        timeout_seconds=timeout_seconds,
        wait_for_deployment=wait_for_deployment,
    )
