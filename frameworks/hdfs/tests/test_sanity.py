import dcos.http
import pytest
import shakedown
import inspect
import os

PACKAGE_NAME = 'hdfs'

HDFS_POD_TYPES = {"journal", "name", "data"}
DEFAULT_HDFS_TASK_COUNT = 10 # 3 data nodes, 3 journal nodes, 2 name nodes, 2 zkfc nodes


def setup_module(module):
    uninstall()
    install()
    check_health()


@pytest.mark.sanity
def test_install_worked():
    pass


@pytest.mark.sanity
def test_bump_journal_cpus():
    check_health()
    journal_ids = get_task_ids('journal')
    print('journal ids: ' + str(journal_ids))

    config = get_marathon_config()
    print('marathon config: ')
    print(config)
    cpus = float(config['env']['JOURNAL_CPUS'])
    config['env']['JOURNAL_CPUS'] = str(cpus + 0.1)
    r = request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    tasks_updated('journal', journal_ids)

    check_health()


@pytest.mark.sanity
def test_bump_data_nodes():
    check_health()

    data_ids = get_task_ids('data')
    print('data ids: ' + str(data_ids))

    config = get_marathon_config()
    nodeCount = int(config['env']['DATA_COUNT']) + 1
    config['env']['DATA_COUNT'] = str(nodeCount)
    r = request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    check_health(DEFAULT_HDFS_TASK_COUNT + 1)
    tasks_not_updated('data', data_ids)
