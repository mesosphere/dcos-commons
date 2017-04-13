import pytest

import sdk_install as install
import sdk_marathon as marathon
import sdk_tasks as tasks
import sdk_utils as utils
from tests.test_utils import (
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_BROKER_COUNT,
    DYNAMIC_PORT_OPTIONS_DICT,
    STATIC_PORT_OPTIONS_DICT,
    DEFAULT_POD_TYPE,
    service_cli
)


def setup_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)
    utils.gc_frameworks()


def teardown_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)

# --------- Port -------------


@pytest.yield_fixture
def dynamic_port_config():
    install.install(PACKAGE_NAME,
                    DEFAULT_BROKER_COUNT,
                    service_name=SERVICE_NAME,
                    additional_options=DYNAMIC_PORT_OPTIONS_DICT)
    yield
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)


@pytest.fixture
def static_port_config():
    install.install(PACKAGE_NAME,
                    DEFAULT_BROKER_COUNT,
                    service_name=SERVICE_NAME,
                    additional_options=STATIC_PORT_OPTIONS_DICT)


@pytest.mark.sanity
def test_dynamic_port_comes_online(dynamic_port_config):
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)


@pytest.mark.sanity
def test_static_port_comes_online(static_port_config):
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)


@pytest.mark.sanity
def test_port_static_to_static_port():
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    broker_ids = tasks.get_task_ids(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE))

    config = marathon.get_config(SERVICE_NAME)
    utils.out('Old Config :{}'.format(config))

    for broker_id in range(DEFAULT_BROKER_COUNT):
        result = service_cli('broker get {}'.format(broker_id))
        assert result['port'] == 9092

    config['env']['BROKER_PORT'] = '9095'
    marathon.update_app(SERVICE_NAME, config)
    utils.out('New Config :{}'.format(config))

    tasks.check_tasks_updated(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE), broker_ids)
    # all tasks are running
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    for broker_id in range(DEFAULT_BROKER_COUNT):
        result = service_cli('broker get {}'.format(broker_id))
        assert result['port'] == 9095


@pytest.mark.sanity
def test_port_static_to_dynamic_port():
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    broker_ids = tasks.get_task_ids(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE))

    config = marathon.get_config(SERVICE_NAME)
    config['env']['BROKER_PORT'] = '0'
    marathon.update_app(SERVICE_NAME, config)

    tasks.check_tasks_updated(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE), broker_ids)
    # all tasks are running
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    for broker_id in range(DEFAULT_BROKER_COUNT):
        result = service_cli('broker get {}'.format(broker_id))
        assert result['port'] != 9092


@pytest.mark.sanity
def test_port_dynamic_to_dynamic_port():
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    broker_ids = tasks.get_task_ids(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE))

    marathon.bump_cpu_count_config(SERVICE_NAME, 'BROKER_CPUS')

    tasks.check_tasks_updated(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE), broker_ids)
    # all tasks are running
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)



@pytest.mark.sanity
def test_can_adjust_config_from_dynamic_to_static_port():
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)
    broker_ids = tasks.get_task_ids(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE))

    config = marathon.get_config(SERVICE_NAME)
    config['env']['BROKER_PORT'] = '9092'
    marathon.update_app(SERVICE_NAME, config)

    tasks.check_tasks_updated(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE), broker_ids)
    # all tasks are running
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    for broker_id in range(DEFAULT_BROKER_COUNT):
        result = service_cli('broker get {}'.format(broker_id))
        assert result['port'] == 9092

