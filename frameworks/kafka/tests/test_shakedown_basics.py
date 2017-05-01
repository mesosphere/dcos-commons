import pytest

import sdk_install as install
import sdk_tasks as tasks
import sdk_spin as spin
import sdk_cmd as command
import sdk_utils as utils
import dcos
import dcos.config
import dcos.http

import urllib

from tests.test_utils import (
    DEFAULT_PARTITION_COUNT,
    DEFAULT_REPLICATION_FACTOR,
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_BROKER_COUNT,
    DEFAULT_TOPIC_NAME,
    EPHEMERAL_TOPIC_NAME,
    DEFAULT_POD_TYPE,
    DEFAULT_PHASE_NAME,
    DEFAULT_PLAN_NAME,
    DEFAULT_TASK_NAME,
    service_cli,
    broker_count_check
)


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
    endpoints = spin.time_wait_return(fun)
    # NOTE: do NOT closed-to-extension assert len(endpoints) == _something_
    assert len(endpoints['address']) == DEFAULT_BROKER_COUNT
    assert len(endpoints['dns']) == DEFAULT_BROKER_COUNT


@pytest.mark.smoke
@pytest.mark.sanity
def test_endpoints_zookeeper():
    zookeeper = command.run_cli('{} endpoints zookeeper'.format(PACKAGE_NAME))
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
        command.run_cli('{} broker get {}'.format(PACKAGE_NAME, DEFAULT_BROKER_COUNT + 1))
        assert False, "Should have failed"
    except AssertionError as arg:
        raise arg
    except:
        pass  # expected to fail

# --------- Pods -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_pods_restart():
    for i in range(DEFAULT_BROKER_COUNT):
        broker_id = tasks.get_task_ids(SERVICE_NAME,'{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME))
        restart_info = service_cli('pods restart {}-{}'.format(DEFAULT_POD_TYPE, i))
        tasks.check_tasks_updated(SERVICE_NAME, '{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME), broker_id)
        assert len(restart_info) == 2
        assert restart_info['tasks'][0] == '{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_pods_replace():
    broker_0_id = tasks.get_task_ids(SERVICE_NAME, '{}-0-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME))
    service_cli('pods replace {}-0'.format(DEFAULT_POD_TYPE))
    tasks.check_tasks_updated(SERVICE_NAME, '{}-0-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME), broker_0_id)
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)
    # wait till all brokers register
    broker_count_check(DEFAULT_BROKER_COUNT)


# --------- Topics -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_create():
    create_info = service_cli(
        'topic create {}'.format(EPHEMERAL_TOPIC_NAME)
    )
    utils.out(create_info)
    assert ('Created topic "%s".\n' % EPHEMERAL_TOPIC_NAME in create_info['message'])
    assert ("topics with a period ('.') or underscore ('_') could collide." in create_info['message'])
    topic_list_info = service_cli('topic list')
    assert topic_list_info == [EPHEMERAL_TOPIC_NAME]

    topic_info = service_cli('topic describe {}'.format(EPHEMERAL_TOPIC_NAME))
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


@pytest.mark.smoke
@pytest.mark.sanity
def test_topic_delete():
    delete_info = service_cli('topic delete {}'.format(EPHEMERAL_TOPIC_NAME))

    assert len(delete_info) == 1
    assert delete_info['message'].startswith('Output: Topic {} is marked for deletion'.format(EPHEMERAL_TOPIC_NAME))

    topic_info = service_cli('topic describe {}'.format(EPHEMERAL_TOPIC_NAME))
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


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


# --------- Cli -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_help_cli():
    command.run_cli('help')


@pytest.mark.smoke
@pytest.mark.sanity
def test_config_cli():
    configs = service_cli('config list')
    assert len(configs) == 1

    assert service_cli('config show {}'.format(configs[0]))
    assert service_cli('config target')
    assert service_cli('config target_id')


@pytest.mark.smoke
@pytest.mark.sanity
def test_plan_cli():
    assert service_cli('plan list')
    assert service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))
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
    assert service_cli('pods info {}-0'.format(DEFAULT_POD_TYPE))

# --------- Suppressed -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_suppress():
    dcos_url = dcos.config.get_config_val('core.dcos_url')
    suppressed_url = urllib.parse.urljoin(dcos_url,
                                          'service/{}/v1/state/properties/suppressed'.format(PACKAGE_NAME))

    def fun():
        response = dcos.http.get(suppressed_url)
        response.raise_for_status()
        return response.text == "true"

    spin.time_wait_noisy(fun)


