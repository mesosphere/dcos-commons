import uuid
import logging
import retrying
import pytest

import sdk_cmd
import sdk_install

from tests import config
from tests import test_utils
from tests import topics


LOG = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def kafka_server(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT)

        # wait for brokers to finish registering before starting tests
        test_utils.broker_count_check(config.DEFAULT_BROKER_COUNT,
                                      service_name=config.SERVICE_NAME)

        # Since the tests below interact with the brokers, ensure that the DNS resolves
        test_utils.wait_for_broker_dns(config.PACKAGE_NAME, config.SERVICE_NAME)

        yield {"package_name": config.PACKAGE_NAME, "service": {"name": config.SERVICE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_create(kafka_server: dict):
    test_utils.create_topic(config.EPHEMERAL_TOPIC_NAME, kafka_server["service"]["name"])


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_delete(kafka_server: dict):
    test_utils.delete_topic(config.EPHEMERAL_TOPIC_NAME, kafka_server["service"]["name"])


@pytest.mark.sanity
def test_topic_partition_count(kafka_server: dict):
    package_name = kafka_server["package_name"]
    service_name = kafka_server["service"]["name"]

    sdk_cmd.svc_cli(
        package_name, service_name,
        'topic create {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    topic_info = sdk_cmd.svc_cli(
        package_name, service_name,
        'topic describe {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    assert len(topic_info['partitions']) == config.DEFAULT_PARTITION_COUNT



@pytest.mark.sanity
def test_topic_offsets_increase_with_writes(kafka_server: dict):
    package_name = kafka_server["package_name"]
    service_name = kafka_server["service"]["name"]

    def offset_is_valid(result) -> bool:
        initial = result[0]
        offsets = result[1]

        LOG.info("Checking validity with initial=%s offsets=%s", initial, offsets)
        has_elements = bool(topics.filter_empty_offsets(offsets, additional=initial))
        # The return of this function triggers the restart.
        return not has_elements

    @retrying.retry(wait_exponential_multiplier=1000,
                    wait_exponential_max=60 * 1000,
                    retry_on_result=offset_is_valid)
    def get_offset_change(topic_name, initial_offsets=[]):
        """
        Run:
            `dcos kafa topic offsets --time="-1"`
        until the output is not the initial output specified
        """
        LOG.info("Getting offsets for %s", topic_name)
        offsets = sdk_cmd.svc_cli(package_name, service_name,
                                  'topic offsets --time="-1" {}'.format(topic_name), json=True)
        LOG.info("offsets=%s", offsets)
        return initial_offsets, offsets

    topic_name = str(uuid.uuid4())
    LOG.info("Creating topic: %s", topic_name)
    test_utils.create_topic(topic_name, service_name)

    _, offset_info = get_offset_change(topic_name)

    # offset_info is a list of (partition index, offset) key-value pairs sum the
    # integer representations of the offsets
    initial_offset = sum(map(lambda partition: sum(map(int, partition.values())), offset_info))
    LOG.info("Initial offset=%s", initial_offset)

    num_messages = 10
    LOG.info("Sending %s messages", num_messages)
    write_info = sdk_cmd.svc_cli(
        package_name, service_name,
        'topic producer_test {} {}'.format(topic_name, num_messages), json=True)
    assert len(write_info) == 1
    assert write_info['message'].startswith('Output: {} records sent'.format(num_messages))

    _, post_write_offset_info = get_offset_change(topic_name, offset_info)

    post_write_offset = sum(map(lambda partition: sum(map(int, partition.values())), post_write_offset_info))
    LOG.info("Post-write offset=%s", post_write_offset)

    assert post_write_offset > initial_offset


@pytest.mark.sanity
def test_decreasing_topic_partitions_fails(kafka_server: dict):
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, kafka_server["service"]["name"],
        'topic partitions {} {}'.format(config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT - 1), json=True)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' in partition_info['message'])


@pytest.mark.sanity
def test_setting_topic_partitions_to_same_value_fails(kafka_server: dict):
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, kafka_server["service"]["name"],
        'topic partitions {} {}'.format(config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT), json=True)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' in partition_info['message'])


@pytest.mark.sanity
def test_increasing_topic_partitions_succeeds(kafka_server: dict):
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, kafka_server["service"]["name"],
        'topic partitions {} {}'.format(config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT + 1), json=True)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' not in partition_info['message'])


@pytest.mark.sanity
def test_no_under_replicated_topics_exist(kafka_server: dict):
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, kafka_server["service"]["name"],
        'topic under_replicated_partitions', json=True)

    assert partition_info == {"message": ""}


@pytest.mark.sanity
def test_no_unavailable_partitions_exist(kafka_server: dict):
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, kafka_server["service"]["name"],
        'topic unavailable_partitions', json=True)

    assert partition_info == {"message": ""}
