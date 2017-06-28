import pytest

import sdk_install as install
import sdk_marathon as marathon
import sdk_tasks as tasks
import sdk_utils as utils
from tests.test_utils import (
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_BROKER_COUNT,
    DEFAULT_POD_TYPE,
    service_cli
)

STATIC_PORT_OPTIONS_DICT = {"brokers": {"port": 9092}}
DYNAMIC_PORT_OPTIONS_DICT = {"brokers": {"port": 0}}


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_universe):
    try:
        install.uninstall(SERVICE_NAME, PACKAGE_NAME)
        utils.gc_frameworks()

        yield # let the test session execute
    finally:
        install.uninstall(SERVICE_NAME, PACKAGE_NAME)


@pytest.mark.sanity
def test_dynamic_port_comes_online():
    install.install(PACKAGE_NAME,
                    DEFAULT_BROKER_COUNT,
                    service_name=SERVICE_NAME,
                    additional_options=DYNAMIC_PORT_OPTIONS_DICT)
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)


@pytest.mark.sanity
def test_static_port_comes_online():
    install.install(PACKAGE_NAME,
                    DEFAULT_BROKER_COUNT,
                    service_name=SERVICE_NAME,
                    additional_options=STATIC_PORT_OPTIONS_DICT)

    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)
    # static config continues to be used in the following tests:


@pytest.mark.sanity
def test_port_static_to_static_port():
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    broker_ids = tasks.get_task_ids(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE))

    config = marathon.get_config(SERVICE_NAME)

    for broker_id in range(DEFAULT_BROKER_COUNT):
        result = service_cli('broker get {}'.format(broker_id))
        assert result['port'] == 9092

    result = service_cli('endpoints broker')
    assert len(result['address']) == DEFAULT_BROKER_COUNT
    assert len(result['dns']) == DEFAULT_BROKER_COUNT

    for port in result['address']:
        assert int(port.split(':')[-1]) == 9092
    for port in result['dns']:
        assert int(port.split(':')[-1]) == 9092

    config['env']['BROKER_PORT'] = '9095'
    marathon.update_app(SERVICE_NAME, config)

    tasks.check_tasks_updated(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE), broker_ids)
    # all tasks are running
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    result = service_cli('endpoints broker')
    assert len(result['address']) == DEFAULT_BROKER_COUNT
    assert len(result['dns']) == DEFAULT_BROKER_COUNT

    for port in result['address']:
        assert int(port.split(':')[-1]) == 9095
    for port in result['dns']:
        assert int(port.split(':')[-1]) == 9095


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

    result = service_cli('endpoints broker')
    assert len(result['address']) == DEFAULT_BROKER_COUNT
    assert len(result['dns']) == DEFAULT_BROKER_COUNT

    for port in result['address']:
        assert int(port.split(':')[-1]) != 9092

    for port in result['dns']:
        assert int(port.split(':')[-1]) != 9092


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

    result = service_cli('endpoints broker')
    assert len(result['address']) == DEFAULT_BROKER_COUNT
    assert len(result['dns']) == DEFAULT_BROKER_COUNT

    for port in result['address']:
        assert int(port.split(':')[-1]) == 9092

    for port in result['dns']:
        assert int(port.split(':')[-1]) == 9092
