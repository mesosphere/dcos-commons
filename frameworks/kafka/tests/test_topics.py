import pytest

import sdk_cmd
import sdk_install
import sdk_utils

from tests import config
from tests import test_utils


@pytest.fixture(scope='module', autouse=True)
def kafka_server(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT)

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
    service_name = kafka_server["service"]["name"]
    offset_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, service_name,
        'topic offsets --time="-1" {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    assert len(offset_info) == config.DEFAULT_PARTITION_COUNT

    offsets = {}
    for o in offset_info:
        assert len(o) == config.DEFAULT_REPLICATION_FACTOR
        offsets.update(o)

    assert len(offsets) == config.DEFAULT_PARTITION_COUNT

    num_messages = 10
    write_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, service_name,
        'topic producer_test {} {}'.format(config.DEFAULT_TOPIC_NAME, num_messages), json=True)
    assert len(write_info) == 1
    assert write_info['message'].startswith('Output: {} records sent'.format(num_messages))

    offset_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, service_name,
        'topic offsets --time="-1" {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    assert len(offset_info) == config.DEFAULT_PARTITION_COUNT

    post_write_offsets = {}
    for offsets in offset_info:
        assert len(o) == config.DEFAULT_REPLICATION_FACTOR
        post_write_offsets.update(o)

    assert not offsets == post_write_offsets


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

    assert len(partition_info) == 1
    assert partition_info['message'] == ''


@pytest.mark.sanity
def test_no_unavailable_partitions_exist(kafka_server: dict):
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, kafka_server["service"]["name"],
        'topic unavailable_partitions', json=True)

    assert len(partition_info) == 0
