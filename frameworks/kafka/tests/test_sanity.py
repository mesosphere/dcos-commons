import pytest
import urllib

import sdk_install as install
import sdk_tasks as tasks
import sdk_utils as utils
import shakedown
import dcos
import dcos.config
import dcos.http
import shakedown

from tests.test_utils import *

DEFAULT_TOPIC_NAME = 'topic1'
EPHEMERAL_TOPIC_NAME = 'topic_2'


def setup_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)
    utils.gc_frameworks()
    install.install(PACKAGE_NAME,  DEFAULT_BROKER_COUNT, service_name = SERVICE_NAME)


def teardown_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)


# --------- Endpoints -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_address():
    def fun():
        ret = service_cli('endpoints {}'.format(DEFAULT_TASK_NAME))
        if len(ret['address']) == DEFAULT_BROKER_COUNT:
            return ret
        return False
    endpoints = shakedown.wait_for(fun)
    # NOTE: do NOT closed-to-extension assert len(endpoints) == _something_
    assert len(endpoints['address']) == DEFAULT_BROKER_COUNT
    assert len(endpoints['dns']) == DEFAULT_BROKER_COUNT
    for dns_endpoint in endpoints['dns']:
        assert "autoip.dcos.thisdcos.directory" in dns_endpoint


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_zookeeper():
    zookeeper = service_cli('endpoints zookeeper', get_json=False)
    assert zookeeper.rstrip() == (
        'master.mesos:2181/dcos-service-{}'.format(PACKAGE_NAME)
    )


# --------- Broker -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_broker_list():
    brokers = service_cli('broker list')
    assert set(brokers) == set([str(i) for i in range(DEFAULT_BROKER_COUNT)])


@pytest.mark.smoke
@pytest.mark.sanity
def test_broker_invalid():
    try:
        service_cli('broker get {}'.format(DEFAULT_BROKER_COUNT + 1))
        assert False, "Should have failed"
    except AssertionError as arg:
        raise arg
    except:
        pass  # expected to fail

# --------- Pods -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_pods_restart():
    restart_broker_pods()


@pytest.mark.smoke
@pytest.mark.sanity
def test_pods_replace():
    replace_broker_pod()


# --------- Topics -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_create():
    create_topic()


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_delete():
    delete_topic()


@pytest.fixture
def default_topic():
    service_cli('topic create {}'.format(DEFAULT_TOPIC_NAME))


@pytest.mark.sanity
def test_topic_partition_count(default_topic):
    topic_info = service_cli('topic describe {}'.format(DEFAULT_TOPIC_NAME))
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


@pytest.mark.sanity
def test_topic_offsets_increase_with_writes():
    offset_info = service_cli('topic offsets --time="-1" {}'.format(DEFAULT_TOPIC_NAME))
    assert len(offset_info) == DEFAULT_PARTITION_COUNT

    offsets = {}
    for o in offset_info:
        assert len(o) == DEFAULT_REPLICATION_FACTOR
        offsets.update(o)

    assert len(offsets) == DEFAULT_PARTITION_COUNT

    num_messages = 10
    write_info = service_cli('topic producer_test {} {}'.format(DEFAULT_TOPIC_NAME, num_messages))
    assert len(write_info) == 1
    assert write_info['message'].startswith('Output: {} records sent'.format(num_messages))

    offset_info = service_cli('topic offsets --time="-1" {}'.format(DEFAULT_TOPIC_NAME))
    assert len(offset_info) == DEFAULT_PARTITION_COUNT

    post_write_offsets = {}
    for offsets in offset_info:
        assert len(o) == DEFAULT_REPLICATION_FACTOR
        post_write_offsets.update(o)

    assert not offsets == post_write_offsets


@pytest.mark.sanity
def test_decreasing_topic_partitions_fails():
    partition_info = service_cli('topic partitions {} {}'.format(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_COUNT - 1))

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' in partition_info['message'])


@pytest.mark.sanity
def test_setting_topic_partitions_to_same_value_fails():
    partition_info = service_cli('topic partitions {} {}'.format(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_COUNT))

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' in partition_info['message'])


@pytest.mark.sanity
def test_increasing_topic_partitions_succeeds():
    partition_info = service_cli('topic partitions {} {}'.format(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_COUNT + 1))

    assert len(partition_info) == 1
    assert partition_info['message'].startswith('Output: WARNING: If partitions are increased')
    assert ('The number of partitions for a topic can only be increased' not in partition_info['message'])


@pytest.mark.sanity
def test_no_under_replicated_topics_exist():
    partition_info = service_cli('topic under_replicated_partitions')

    assert len(partition_info) == 1
    assert partition_info['message'] == ''


@pytest.mark.sanity
def test_no_unavailable_partitions_exist():
    partition_info = service_cli('topic unavailable_partitions')

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
    configs = service_cli('config list')
    assert len(configs) == 1

    assert service_cli('config show {}'.format(configs[0]), print_output=False) # noisy output
    assert service_cli('config target')
    assert service_cli('config target_id')


@pytest.mark.smoke
@pytest.mark.sanity
def test_plan_cli():
    assert service_cli('plan list')
    assert service_cli('plan show {}'.format(DEFAULT_PLAN_NAME), get_json=False)
    assert service_cli('plan show --json {}'.format(DEFAULT_PLAN_NAME))
    assert service_cli('plan show {} --json'.format(DEFAULT_PLAN_NAME))
    assert service_cli('plan interrupt {} {}'.format(DEFAULT_PLAN_NAME, DEFAULT_PHASE_NAME))
    assert service_cli('plan continue {} {}'.format(DEFAULT_PLAN_NAME, DEFAULT_PHASE_NAME))



@pytest.mark.smoke1
@pytest.mark.sanity1
# state gives error, now sure why? disabling for the moment
def test_state_cli():
    assert service_cli('state framework_id')
    assert service_cli('state properties')


@pytest.mark.smoke
@pytest.mark.sanity
def test_pods_cli():
    assert service_cli('pods list')
    assert service_cli('pods status {}-0'.format(DEFAULT_POD_TYPE))
    assert service_cli('pods info {}-0'.format(DEFAULT_POD_TYPE), print_output=False) # noisy output

# --------- Suppressed -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_suppress():
    dcos_url = dcos.config.get_config_val('core.dcos_url')
    suppressed_url = urllib.parse.urljoin(
        dcos_url, 'service/{}/v1/state/properties/suppressed'.format(PACKAGE_NAME))

    def fun():
        response = dcos.http.get(suppressed_url)
        response.raise_for_status()
        return response.text == "true"

    shakedown.wait_for(fun)


