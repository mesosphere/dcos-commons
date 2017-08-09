import pytest
import urllib

import sdk_hosts
import sdk_install as install
import sdk_marathon
import sdk_metrics
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils

import dcos
import dcos.config
import dcos.http
import shakedown

from tests.test_utils import *
from tests.config import *

DEFAULT_TOPIC_NAME = 'topic1'
EPHEMERAL_TOPIC_NAME = 'topic_2'
FOLDERED_SERVICE_NAME = sdk_utils.get_foldered_name(PACKAGE_NAME)
ZK_SERVICE_PATH = sdk_utils.get_zk_path(PACKAGE_NAME)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)

        if shakedown.dcos_version_less_than("1.9"):
            # Last beta-kafka release (1.1.25-0.10.1.0-beta) excludes 1.8. Skip upgrade tests with 1.8 and just install
            install.install(
                PACKAGE_NAME,
                DEFAULT_BROKER_COUNT,
                service_name=FOLDERED_SERVICE_NAME,
                additional_options={"service": { "name": FOLDERED_SERVICE_NAME } })
        else:
            sdk_upgrade.test_upgrade(
                "beta-{}".format(PACKAGE_NAME),
                PACKAGE_NAME,
                DEFAULT_BROKER_COUNT,
                service_name=FOLDERED_SERVICE_NAME,
                additional_options={"service": {"name": FOLDERED_SERVICE_NAME} })

        yield # let the test session execute
    finally:
        install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)


# --------- Endpoints -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_address():
    def fun():
        ret = service_cli('endpoints {}'.format(DEFAULT_TASK_NAME), service_name=FOLDERED_SERVICE_NAME)
        if len(ret['address']) == DEFAULT_BROKER_COUNT:
            return ret
        return False
    endpoints = shakedown.wait_for(fun)
    # NOTE: do NOT closed-to-extension assert len(endpoints) == _something_
    assert len(endpoints['address']) == DEFAULT_BROKER_COUNT
    assert len(endpoints['dns']) == DEFAULT_BROKER_COUNT
    for i in range(len(endpoints['dns'])):
        assert sdk_hosts.autoip_host(FOLDERED_SERVICE_NAME, 'kafka-{}-broker'.format(i)) in endpoints['dns'][i]
    assert endpoints['vip'] == sdk_hosts.vip_host(FOLDERED_SERVICE_NAME, 'broker', 9092)


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_zookeeper_default():
    zookeeper = service_cli('endpoints zookeeper', get_json=False, service_name=FOLDERED_SERVICE_NAME)
    assert zookeeper.rstrip('\n') == 'master.mesos:2181/dcos-service-{}'.format(ZK_SERVICE_PATH)


@pytest.mark.smoke
@pytest.mark.sanity
def test_custom_zookeeper():
    broker_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE))

    # sanity check: brokers should be reinitialized:
    brokers = service_cli('broker list', service_name=FOLDERED_SERVICE_NAME)
    assert set(brokers) == set([str(i) for i in range(DEFAULT_BROKER_COUNT)])

    # create a topic against the default zk:
    service_cli('topic create {}'.format(DEFAULT_TOPIC_NAME), service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('topic list', service_name=FOLDERED_SERVICE_NAME) == [DEFAULT_TOPIC_NAME]

    config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    # should be using default path when this envvar is empty/unset:
    assert config['env']['KAFKA_ZOOKEEPER_URI'] == ''


    # use a custom zk path that's WITHIN the 'dcos-service-' path, so that it's automatically cleaned up in uninstall:
    zk_path = 'master.mesos:2181/dcos-service-{}/CUSTOMPATH'.format(ZK_SERVICE_PATH)
    config['env']['KAFKA_ZOOKEEPER_URI'] = zk_path
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, config)

    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE), broker_ids)
    sdk_plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME)

    zookeeper = service_cli('endpoints zookeeper', get_json=False, service_name=FOLDERED_SERVICE_NAME)
    assert zookeeper.rstrip('\n') == zk_path

    # topic created earlier against default zk should no longer be present:
    assert service_cli('topic list', service_name=FOLDERED_SERVICE_NAME) == []

    # tests from here continue with the custom ZK path...

# --------- Broker -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_broker_list():
    brokers = service_cli('broker list', service_name=FOLDERED_SERVICE_NAME)
    assert set(brokers) == set([str(i) for i in range(DEFAULT_BROKER_COUNT)])


@pytest.mark.smoke
@pytest.mark.sanity
def test_broker_invalid():
    try:
        service_cli('broker get {}'.format(DEFAULT_BROKER_COUNT + 1), service_name=FOLDERED_SERVICE_NAME)
        assert False, "Should have failed"
    except AssertionError as arg:
        raise arg
    except:
        pass  # expected to fail


# --------- Pods -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_pods_restart():
    restart_broker_pods(FOLDERED_SERVICE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_pods_replace():
    replace_broker_pod(FOLDERED_SERVICE_NAME)


# --------- Topics -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_create():
    create_topic(FOLDERED_SERVICE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_delete():
    delete_topic(FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
def test_topic_partition_count():
    service_cli('topic create {}'.format(DEFAULT_TOPIC_NAME), service_name=FOLDERED_SERVICE_NAME)

    topic_info = service_cli('topic describe {}'.format(DEFAULT_TOPIC_NAME), service_name=FOLDERED_SERVICE_NAME)
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


@pytest.mark.sanity
def test_topic_offsets_increase_with_writes():
    offset_info = service_cli('topic offsets --time="-1" {}'.format(DEFAULT_TOPIC_NAME), service_name=FOLDERED_SERVICE_NAME)
    assert len(offset_info) == DEFAULT_PARTITION_COUNT

    offsets = {}
    for o in offset_info:
        assert len(o) == DEFAULT_REPLICATION_FACTOR
        offsets.update(o)

    assert len(offsets) == DEFAULT_PARTITION_COUNT

    num_messages = 10
    write_info = service_cli('topic producer_test {} {}'.format(DEFAULT_TOPIC_NAME, num_messages), service_name=FOLDERED_SERVICE_NAME)
    assert len(write_info) == 1
    assert write_info['message'].startswith('Output: {} records sent'.format(num_messages))

    offset_info = service_cli('topic offsets --time="-1" {}'.format(DEFAULT_TOPIC_NAME), service_name=FOLDERED_SERVICE_NAME)
    assert len(offset_info) == DEFAULT_PARTITION_COUNT

    post_write_offsets = {}
    for offsets in offset_info:
        assert len(o) == DEFAULT_REPLICATION_FACTOR
        post_write_offsets.update(o)

    assert not offsets == post_write_offsets


@pytest.mark.sanity
def test_decreasing_topic_partitions_fails():
    partition_info = service_cli('topic partitions {} {}'.format(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_COUNT - 1), service_name=FOLDERED_SERVICE_NAME)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' in partition_info['message'])


@pytest.mark.sanity
def test_setting_topic_partitions_to_same_value_fails():
    partition_info = service_cli('topic partitions {} {}'.format(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_COUNT), service_name=FOLDERED_SERVICE_NAME)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' in partition_info['message'])


@pytest.mark.sanity
def test_increasing_topic_partitions_succeeds():
    partition_info = service_cli('topic partitions {} {}'.format(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_COUNT + 1), service_name=FOLDERED_SERVICE_NAME)

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' not in partition_info['message'])


@pytest.mark.sanity
def test_no_under_replicated_topics_exist():
    partition_info = service_cli('topic under_replicated_partitions', service_name=FOLDERED_SERVICE_NAME)

    assert len(partition_info) == 1
    assert partition_info['message'] == ''


@pytest.mark.sanity
def test_no_unavailable_partitions_exist():
    partition_info = service_cli('topic unavailable_partitions', service_name=FOLDERED_SERVICE_NAME)

    assert len(partition_info) == 1
    assert partition_info['message'] == ''


# --------- CLI -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_help_cli():
    service_cli('help', get_json=False)


@pytest.mark.smoke
@pytest.mark.sanity
def test_config_cli():
    configs = service_cli('config list', service_name=FOLDERED_SERVICE_NAME)
    assert len(configs) >= 1 # refrain from breaking this test if earlier tests did a config update

    assert service_cli('config show {}'.format(configs[0]), service_name=FOLDERED_SERVICE_NAME, print_output=False) # noisy output
    assert service_cli('config target', service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('config target_id', service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_plan_cli():
    assert service_cli('plan list', service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('plan show {}'.format(DEFAULT_PLAN_NAME), get_json=False, service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('plan show --json {}'.format(DEFAULT_PLAN_NAME), service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('plan show {} --json'.format(DEFAULT_PLAN_NAME), service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('plan force-restart {}'.format(DEFAULT_PLAN_NAME), get_json=False, service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('plan interrupt {} {}'.format(DEFAULT_PLAN_NAME, DEFAULT_PHASE_NAME), get_json=False, service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('plan continue {} {}'.format(DEFAULT_PLAN_NAME, DEFAULT_PHASE_NAME), get_json=False, service_name=FOLDERED_SERVICE_NAME)



@pytest.mark.smoke
@pytest.mark.sanity
def test_state_cli():
    assert service_cli('state framework_id', service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('state properties', service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_pod_cli():
    assert service_cli('pod list', service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('pod status {}-0'.format(DEFAULT_POD_TYPE), service_name=FOLDERED_SERVICE_NAME)
    assert service_cli('pod info {}-0'.format(DEFAULT_POD_TYPE), service_name=FOLDERED_SERVICE_NAME, print_output=False) # noisy output

@pytest.mark.sanity
@pytest.mark.metrics
@sdk_utils.dcos_1_9_or_higher
def test_metrics():
    sdk_metrics.wait_for_any_metrics(FOLDERED_SERVICE_NAME, "kafka-0-broker", DEFAULT_KAFKA_TIMEOUT)


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
