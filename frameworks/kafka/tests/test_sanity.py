import urllib

import dcos
import dcos.config
import dcos.http
import pytest
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_metrics
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils
import shakedown
from tests import config, test_utils


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        if sdk_utils.dcos_version_less_than("1.9"):
            # Last beta-kafka release (1.1.25-0.10.1.0-beta) excludes 1.8. Skip upgrade tests with 1.8 and just install
            sdk_install.install(
                config.PACKAGE_NAME,
                foldered_name,
                config.DEFAULT_BROKER_COUNT,
                additional_options={"service": {"name": foldered_name}})
        else:
            sdk_upgrade.test_upgrade(
                config.PACKAGE_NAME,
                foldered_name,
                config.DEFAULT_BROKER_COUNT,
                additional_options={"service": {"name": foldered_name}, "brokers": {"cpus": 0.5}})

        # wait for brokers to finish registering before starting tests
        test_utils.broker_count_check(config.DEFAULT_BROKER_COUNT,
                                      service_name=foldered_name)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


# --------- Endpoints -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_address():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    def fun():
        ret = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, foldered_name,
            'endpoints {}'.format(config.DEFAULT_TASK_NAME), json=True)
        if len(ret['address']) == config.DEFAULT_BROKER_COUNT:
            return ret
        return False
    endpoints = shakedown.wait_for(fun)
    # NOTE: do NOT closed-to-extension assert len(endpoints) == _something_
    assert len(endpoints['address']) == config.DEFAULT_BROKER_COUNT
    assert len(endpoints['dns']) == config.DEFAULT_BROKER_COUNT
    for i in range(len(endpoints['dns'])):
        assert sdk_hosts.autoip_host(foldered_name, 'kafka-{}-broker'.format(i)) in endpoints['dns'][i]
    assert endpoints['vip'] == sdk_hosts.vip_host(foldered_name, 'broker', 9092)


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_zookeeper_default():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    zookeeper = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'endpoints zookeeper')
    assert zookeeper.rstrip('\n') == 'master.mesos:2181/{}'.format(sdk_utils.get_zk_path(foldered_name))


@pytest.mark.smoke
@pytest.mark.sanity
def test_custom_zookeeper():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    broker_ids = sdk_tasks.get_task_ids(foldered_name, '{}-'.format(config.DEFAULT_POD_TYPE))

    # create a topic against the default zk:
    test_utils.create_topic(config.DEFAULT_TOPIC_NAME, service_name=foldered_name)

    marathon_config = sdk_marathon.get_config(foldered_name)
    # should be using default path when this envvar is empty/unset:
    assert marathon_config['env']['KAFKA_ZOOKEEPER_URI'] == ''

    # use a custom zk path that's WITHIN the 'dcos-service-' path, so that it's automatically cleaned up in uninstall:
    zk_path = 'master.mesos:2181/{}/CUSTOMPATH'.format(sdk_utils.get_zk_path(foldered_name))
    marathon_config['env']['KAFKA_ZOOKEEPER_URI'] = zk_path
    sdk_marathon.update_app(foldered_name, marathon_config)

    sdk_tasks.check_tasks_updated(foldered_name, '{}-'.format(config.DEFAULT_POD_TYPE), broker_ids)
    sdk_plan.wait_for_completed_deployment(foldered_name)

    # wait for brokers to finish registering
    test_utils.broker_count_check(config.DEFAULT_BROKER_COUNT, service_name=foldered_name)

    zookeeper = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'endpoints zookeeper')
    assert zookeeper.rstrip('\n') == zk_path

    # topic created earlier against default zk should no longer be present:
    topic_list_info = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'topic list', json=True)

    test_utils.assert_topic_lists_are_equal_without_automatic_topics([], topic_list_info)

    # tests from here continue with the custom ZK path...


# --------- Broker -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_broker_list():
    brokers = sdk_cmd.svc_cli(config.PACKAGE_NAME,
                              sdk_utils.get_foldered_name(config.SERVICE_NAME), 'broker list', json=True)
    assert set(brokers) == set([str(i) for i in range(config.DEFAULT_BROKER_COUNT)])


@pytest.mark.smoke
@pytest.mark.sanity
def test_broker_invalid():
    try:
        sdk_cmd.svc_cli(
            config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME),
            'broker get {}'.format(config.DEFAULT_BROKER_COUNT + 1), json=True)
        assert False, "Should have failed"
    except AssertionError as arg:
        raise arg
    except:
        pass  # expected to fail


# --------- Pods -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_pods_restart():
    test_utils.restart_broker_pods(sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.smoke
@pytest.mark.sanity
def test_pod_replace():
    test_utils.replace_broker_pod(sdk_utils.get_foldered_name(config.SERVICE_NAME))


# --------- Topics -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_create():
    test_utils.create_topic(config.EPHEMERAL_TOPIC_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_delete():
    test_utils.delete_topic(config.EPHEMERAL_TOPIC_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.sanity
def test_topic_partition_count():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    sdk_cmd.svc_cli(
        config.PACKAGE_NAME, foldered_name,
        'topic create {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    topic_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, foldered_name,
        'topic describe {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    assert len(topic_info['partitions']) == config.DEFAULT_PARTITION_COUNT


@pytest.mark.sanity
def test_topic_offsets_increase_with_writes():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    offset_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, foldered_name,
        'topic offsets --time="-1" {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    assert len(offset_info) == config.DEFAULT_PARTITION_COUNT

    offsets = {}
    for o in offset_info:
        assert len(o) == config.DEFAULT_REPLICATION_FACTOR
        offsets.update(o)

    assert len(offsets) == config.DEFAULT_PARTITION_COUNT

    num_messages = 10
    write_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, foldered_name,
        'topic producer_test {} {}'.format(config.DEFAULT_TOPIC_NAME, num_messages), json=True)
    assert len(write_info) == 1
    assert write_info['message'].startswith('Output: {} records sent'.format(num_messages))

    offset_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, foldered_name,
        'topic offsets --time="-1" {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    assert len(offset_info) == config.DEFAULT_PARTITION_COUNT

    post_write_offsets = {}
    for offsets in offset_info:
        assert len(o) == config.DEFAULT_REPLICATION_FACTOR
        post_write_offsets.update(o)

    assert not offsets == post_write_offsets


@pytest.mark.sanity
def test_decreasing_topic_partitions_fails():
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME),
        'topic partitions {} {}'.format(config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT - 1), json=True)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' in partition_info['message'])


@pytest.mark.sanity
def test_setting_topic_partitions_to_same_value_fails():
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME),
        'topic partitions {} {}'.format(config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT), json=True)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' in partition_info['message'])


@pytest.mark.sanity
def test_increasing_topic_partitions_succeeds():
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME),
        'topic partitions {} {}'.format(config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT + 1), json=True)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' not in partition_info['message'])


@pytest.mark.sanity
def test_no_under_replicated_topics_exist():
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME),
        'topic under_replicated_partitions', json=True)

    assert len(partition_info) == 1
    assert partition_info['message'] == ''


@pytest.mark.sanity
def test_no_unavailable_partitions_exist():
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME),
        'topic unavailable_partitions', json=True)

    assert len(partition_info) == 1
    assert partition_info['message'] == ''


# --------- CLI -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_help_cli():
    sdk_cmd.svc_cli(config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME), 'help')


@pytest.mark.smoke
@pytest.mark.sanity
def test_config_cli():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    configs = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'config list', json=True)
    assert len(configs) >= 1  # refrain from breaking this test if earlier tests did a config update

    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
        'config show {}'.format(configs[0]), print_output=False) # noisy output
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'config target', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'config target_id', json=True)


@pytest.mark.smoke
@pytest.mark.sanity
def test_plan_cli():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'plan list', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'plan show {}'.format(config.DEFAULT_PLAN_NAME))
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
        'plan show --json {}'.format(config.DEFAULT_PLAN_NAME), json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
        'plan show {} --json'.format(config.DEFAULT_PLAN_NAME), json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'plan force-restart {}'.format(config.DEFAULT_PLAN_NAME))
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
        'plan interrupt {} {}'.format(config.DEFAULT_PLAN_NAME, config.DEFAULT_PHASE_NAME))
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
        'plan continue {} {}'.format(config.DEFAULT_PLAN_NAME, config.DEFAULT_PHASE_NAME))


@pytest.mark.smoke
@pytest.mark.sanity
def test_state_cli():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'state framework_id', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'state properties', json=True)


@pytest.mark.smoke
@pytest.mark.sanity
def test_pod_cli():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod list', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
        'pod status --json {}-0'.format(config.DEFAULT_POD_TYPE), json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name,
        'pod info {}-0'.format(config.DEFAULT_POD_TYPE), print_output=False)  # noisy output


@pytest.mark.sanity
@pytest.mark.metrics
@pytest.mark.dcos_min_version('1.9')
def test_metrics():
    expected_metrics = [
        "kafka.network.RequestMetrics.ResponseQueueTimeMs.max",
        "kafka.socket-server-metrics.io-ratio",
        "kafka.controller.ControllerStats.LeaderElectionRateAndTimeMs.p95"
    ]

    def expected_metrics_exist(emitted_metrics):
        return sdk_metrics.check_metrics_presence(emitted_metrics, expected_metrics)

    sdk_metrics.wait_for_service_metrics(
        config.PACKAGE_NAME,
        sdk_utils.get_foldered_name(config.SERVICE_NAME),
        "kafka-0-broker",
        config.DEFAULT_KAFKA_TIMEOUT,
        expected_metrics_exist
    )

