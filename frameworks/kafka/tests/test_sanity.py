import urllib

import dcos
import dcos.config
import dcos.http
import pytest
import sdk_cmd
import sdk_hosts
import sdk_install as install
import sdk_marathon
import sdk_metrics
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils
import shakedown
from tests import config, test_utils

EPHEMERAL_TOPIC_NAME = 'topic_2'
FOLDERED_SERVICE_NAME = sdk_utils.get_foldered_name(config.SERVICE_NAME)
ZK_SERVICE_PATH = sdk_utils.get_zk_path(FOLDERED_SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        install.uninstall(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME)

        if shakedown.dcos_version_less_than("1.9"):
            # Last beta-kafka release (1.1.25-0.10.1.0-beta) excludes 1.8. Skip upgrade tests with 1.8 and just install
            install.install(
                config.PACKAGE_NAME,
                FOLDERED_SERVICE_NAME,
                config.DEFAULT_BROKER_COUNT,
                additional_options={"service": {"name": FOLDERED_SERVICE_NAME}})
        else:
            sdk_upgrade.test_upgrade(
                config.PACKAGE_NAME,
                FOLDERED_SERVICE_NAME,
                config.DEFAULT_BROKER_COUNT,
                additional_options={"service": {"name": FOLDERED_SERVICE_NAME}})

        # wait for brokers to finish registering before starting tests
        test_utils.broker_count_check(config.DEFAULT_BROKER_COUNT, service_name=FOLDERED_SERVICE_NAME)

        yield  # let the test session execute
    finally:
        install.uninstall(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME)


# --------- Endpoints -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_address():
    def fun():
        ret = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
            'endpoints {}'.format(config.DEFAULT_TASK_NAME), json=True)
        if len(ret['address']) == config.DEFAULT_BROKER_COUNT:
            return ret
        return False
    endpoints = shakedown.wait_for(fun)
    # NOTE: do NOT closed-to-extension assert len(endpoints) == _something_
    assert len(endpoints['address']) == config.DEFAULT_BROKER_COUNT
    assert len(endpoints['dns']) == config.DEFAULT_BROKER_COUNT
    for i in range(len(endpoints['dns'])):
        assert sdk_hosts.autoip_host(
            FOLDERED_SERVICE_NAME, 'kafka-{}-broker'.format(i)) in endpoints['dns'][i]
    assert endpoints['vip'] == sdk_hosts.vip_host(
        FOLDERED_SERVICE_NAME, 'broker', 9092)


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_zookeeper_default():
    zookeeper = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'endpoints zookeeper')
    assert zookeeper.rstrip('\n') == 'master.mesos:2181/{}'.format(ZK_SERVICE_PATH)


@pytest.mark.smoke
@pytest.mark.sanity
def test_custom_zookeeper():
    broker_ids = sdk_tasks.get_task_ids(
        FOLDERED_SERVICE_NAME, '{}-'.format(config.DEFAULT_POD_TYPE))

    # create a topic against the default zk:
    sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic create {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    assert sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic list', json=True) == [config.DEFAULT_TOPIC_NAME]

    marathon_config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    # should be using default path when this envvar is empty/unset:
    assert marathon_config['env']['KAFKA_ZOOKEEPER_URI'] == ''

    # use a custom zk path that's WITHIN the 'dcos-service-' path, so that it's automatically cleaned up in uninstall:
    zk_path = 'master.mesos:2181/{}/CUSTOMPATH'.format(ZK_SERVICE_PATH)
    marathon_config['env']['KAFKA_ZOOKEEPER_URI'] = zk_path
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, marathon_config)

    sdk_tasks.check_tasks_updated(
        FOLDERED_SERVICE_NAME, '{}-'.format(config.DEFAULT_POD_TYPE), broker_ids)
    sdk_plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME)

    # wait for brokers to finish registering
    test_utils.broker_count_check(config.DEFAULT_BROKER_COUNT, service_name=FOLDERED_SERVICE_NAME)

    zookeeper = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'endpoints zookeeper')
    assert zookeeper.rstrip('\n') == zk_path

    # topic created earlier against default zk should no longer be present:
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'topic list', json=True) == []

    # tests from here continue with the custom ZK path...


# --------- Broker -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_broker_list():
    brokers = sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'broker list', json=True)
    assert set(brokers) == set([str(i) for i in range(config.DEFAULT_BROKER_COUNT)])


@pytest.mark.smoke
@pytest.mark.sanity
def test_broker_invalid():
    try:
        sdk_cmd.svc_cli(
            config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
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
    test_utils.restart_broker_pods(FOLDERED_SERVICE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_pod_replace():
    test_utils.replace_broker_pod(FOLDERED_SERVICE_NAME)


# --------- Topics -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_create():
    test_utils.create_topic(FOLDERED_SERVICE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_delete():
    test_utils.delete_topic(FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
def test_topic_partition_count():
    sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic create {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    topic_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic describe {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    assert len(topic_info['partitions']) == config.DEFAULT_PARTITION_COUNT


@pytest.mark.sanity
def test_topic_offsets_increase_with_writes():
    offset_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic offsets --time="-1" {}'.format(config.DEFAULT_TOPIC_NAME), json=True)
    assert len(offset_info) == config.DEFAULT_PARTITION_COUNT

    offsets = {}
    for o in offset_info:
        assert len(o) == config.DEFAULT_REPLICATION_FACTOR
        offsets.update(o)

    assert len(offsets) == config.DEFAULT_PARTITION_COUNT

    num_messages = 10
    write_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic producer_test {} {}'.format(config.DEFAULT_TOPIC_NAME, num_messages), json=True)
    assert len(write_info) == 1
    assert write_info['message'].startswith(
        'Output: {} records sent'.format(num_messages))

    offset_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
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
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic partitions {} {}'.format(config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT - 1), json=True)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith(
        'Output: WARNING: If partitions are increased')
    assert (
        'The number of partitions for a topic can only be increased' in partition_info['message'])


@pytest.mark.sanity
def test_setting_topic_partitions_to_same_value_fails():
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic partitions {} {}'.format(config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT), json=True)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith(
        'Output: WARNING: If partitions are increased')
    assert (
        'The number of partitions for a topic can only be increased' in partition_info['message'])


@pytest.mark.sanity
def test_increasing_topic_partitions_succeeds():
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic partitions {} {}'.format(config.DEFAULT_TOPIC_NAME, config.DEFAULT_PARTITION_COUNT + 1), json=True)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith(
        'Output: WARNING: If partitions are increased')
    assert (
        'The number of partitions for a topic can only be increased' not in partition_info['message'])


@pytest.mark.sanity
def test_no_under_replicated_topics_exist():
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic under_replicated_partitions', json=True)

    assert len(partition_info) == 1
    assert partition_info['message'] == ''


@pytest.mark.sanity
def test_no_unavailable_partitions_exist():
    partition_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FOLDERED_SERVICE_NAME,
        'topic unavailable_partitions', json=True)

    assert len(partition_info) == 1
    assert partition_info['message'] == ''


# --------- CLI -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_help_cli():
    sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'help')


@pytest.mark.smoke
@pytest.mark.sanity
def test_config_cli():
    configs = sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'config list', json=True)
    assert len(configs) >= 1  # refrain from breaking this test if earlier tests did a config update

    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'config show {}'.format(configs[0]), print_output=False) # noisy output
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'config target', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'config target_id', json=True)


@pytest.mark.smoke
@pytest.mark.sanity
def test_plan_cli():
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'plan list', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'plan show {}'.format(config.DEFAULT_PLAN_NAME))
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'plan show --json {}'.format(config.DEFAULT_PLAN_NAME), json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'plan show {} --json'.format(config.DEFAULT_PLAN_NAME), json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'plan force-restart {}'.format(config.DEFAULT_PLAN_NAME))
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'plan interrupt {} {}'.format(config.DEFAULT_PLAN_NAME, config.DEFAULT_PHASE_NAME))
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'plan continue {} {}'.format(config.DEFAULT_PLAN_NAME, config.DEFAULT_PHASE_NAME))


@pytest.mark.smoke
@pytest.mark.sanity
def test_state_cli():
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'state framework_id', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'state properties', json=True)


@pytest.mark.smoke
@pytest.mark.sanity
def test_pod_cli():
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'pod list', json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'pod status {}-0'.format(config.DEFAULT_POD_TYPE), json=True)
    assert sdk_cmd.svc_cli(config.PACKAGE_NAME, FOLDERED_SERVICE_NAME, 'pod info {}-0'.format(config.DEFAULT_POD_TYPE), print_output=False)  # noisy output


@pytest.mark.sanity
@pytest.mark.metrics
@sdk_utils.dcos_1_9_or_higher
def test_metrics():
    sdk_metrics.wait_for_any_metrics(
        config.PACKAGE_NAME,
        FOLDERED_SERVICE_NAME,
        "kafka-0-broker",
        config.DEFAULT_KAFKA_TIMEOUT)


# --------- Suppressed -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_suppress():
    dcos_url = dcos.config.get_config_val('core.dcos_url')
    suppressed_url = urllib.parse.urljoin(
        dcos_url, 'service/{}/v1/state/properties/suppressed'.format(FOLDERED_SERVICE_NAME))

    def fun():
        response = dcos.http.get(suppressed_url)
        response.raise_for_status()
        return response.text == "true"

    shakedown.wait_for(fun)
