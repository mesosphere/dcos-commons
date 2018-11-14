import json
import logging
import retrying
import pytest
import uuid

from sdk.testing import sdk_cmd
from sdk.testing import sdk_install

from tests import config
from tests import topics
from tests import client


LOG = logging.getLogger(__name__)


@pytest.fixture(scope="module")
def kafka_client():
    try:
        kafka_client = client.KafkaClient("kafka-client", config.PACKAGE_NAME, config.SERVICE_NAME)
        kafka_client.install()
        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.fixture(scope="module", autouse=True)
def kafka_server(configure_security, kafka_client: client.KafkaClient):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(config.PACKAGE_NAME, config.SERVICE_NAME, config.DEFAULT_BROKER_COUNT)
        kafka_client.connect(config.DEFAULT_BROKER_COUNT)
        yield
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_create(kafka_client: client.KafkaClient):
    kafka_client.check_topic_creation(config.EPHEMERAL_TOPIC_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_delete(kafka_client: client.KafkaClient):
    kafka_client.check_topic_deletion(config.EPHEMERAL_TOPIC_NAME)


@pytest.mark.sanity
def test_topic_partition_count(kafka_client: client.KafkaClient):
    kafka_client.check_topic_creation(config.DEFAULT_TOPIC_NAME)
    kafka_client.check_topic_partition_count(
        config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT
    )


@pytest.mark.sanity
def test_topic_offsets_increase_with_writes(kafka_client: client.KafkaClient):
    package_name = config.PACKAGE_NAME
    service_name = config.SERVICE_NAME

    def offset_is_valid(result) -> bool:
        initial = result[0]
        offsets = result[1]

        LOG.info("Checking validity with initial=%s offsets=%s", initial, offsets)
        has_elements = bool(topics.filter_empty_offsets(offsets, additional=initial))
        # The return of this function triggers the restart.
        return not has_elements

    @retrying.retry(
        stop_max_delay=5 * 60 * 1000,
        wait_exponential_multiplier=1000,
        wait_exponential_max=60 * 1000,
        retry_on_result=offset_is_valid,
    )
    def get_offset_change(topic_name, initial_offsets=[]):
        """
        Run:
            `dcos kafa topic offsets --time="-1"`
        until the output is not the initial output specified
        """
        LOG.info("Getting offsets for %s", topic_name)
        rc, stdout, _ = sdk_cmd.svc_cli(
            package_name, service_name, 'topic offsets --time="-1" {}'.format(topic_name)
        )
        assert rc == 0, "Topic offsets failed"
        offsets = json.loads(stdout)
        LOG.info("offsets=%s", offsets)
        return initial_offsets, offsets

    topic_name = str(uuid.uuid4())
    LOG.info("Creating topic: %s", topic_name)
    kafka_client.create_topic(topic_name)

    _, offset_info = get_offset_change(topic_name)

    # offset_info is a list of (partition index, offset) key-value pairs sum the
    # integer representations of the offsets
    initial_offset = sum(map(lambda partition: sum(map(int, partition.values())), offset_info))
    LOG.info("Initial offset=%s", initial_offset)

    num_messages = 10
    LOG.info("Sending %s messages", num_messages)
    rc, stdout, _ = sdk_cmd.svc_cli(
        package_name, service_name, "topic producer_test {} {}".format(topic_name, num_messages)
    )
    assert rc == 0, "Producer test failed"
    write_info = json.loads(stdout)
    assert len(write_info) == 1
    assert write_info["message"].startswith("Output: {} records sent".format(num_messages))

    _, post_write_offset_info = get_offset_change(topic_name, offset_info)

    post_write_offset = sum(
        map(lambda partition: sum(map(int, partition.values())), post_write_offset_info)
    )
    LOG.info("Post-write offset=%s", post_write_offset)

    assert post_write_offset > initial_offset


@pytest.mark.sanity
def test_decreasing_topic_partitions_fails(kafka_client: client.KafkaClient):
    partition_info = kafka_client.check_topic_partition_change(
        config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT - 1
    )
    assert "The number of partitions for a topic can only be increased" in partition_info


@pytest.mark.sanity
def test_setting_topic_partitions_to_same_value_fails(kafka_client: client.KafkaClient):
    partition_info = kafka_client.check_topic_partition_change(
        config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT
    )
    assert "The number of partitions for a topic can only be increased" in partition_info


@pytest.mark.sanity
def test_increasing_topic_partitions_succeeds(kafka_client: client.KafkaClient):
    partition_info = kafka_client.check_topic_partition_change(
        config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT + 1
    )
    assert "The number of partitions for a topic can only be increased" not in partition_info


@pytest.mark.sanity
def test_no_under_replicated_topics_exist():
    rc, stdout, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "topic under_replicated_partitions"
    )
    assert rc == 0, "Under-replicated partitions failed"
    assert json.loads(stdout) == {"message": ""}


@pytest.mark.sanity
def test_no_unavailable_partitions_exist():
    rc, stdout, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, "topic unavailable_partitions"
    )
    assert rc == 0, "Unavailable partitions failed"
    assert json.loads(stdout) == {"message": ""}
