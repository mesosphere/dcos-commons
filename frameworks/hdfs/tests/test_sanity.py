import dcos.http
import pytest
import shakedown
import inspect
import os

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_tasks as tasks

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    check_running
)

HDFS_POD_TYPES = {"journal", "name", "data"}


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


@pytest.mark.sanity
def test_bump_journal_cpus():
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    print('journal ids: ' + str(journal_ids))

    config = marathon.get_config(PACKAGE_NAME)
    print('marathon config: ')
    print(config)
    cpus = float(config['env']['JOURNAL_CPUS'])
    config['env']['JOURNAL_CPUS'] = str(cpus + 0.1)
    cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    tasks.check_tasks_updated(PACKAGE_NAME, 'journal', journal_ids)
    check_running()


@pytest.mark.sanity
def test_bump_data_nodes():
    check_running()

    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')
    print('data ids: ' + str(data_ids))

    config = marathon.get_config(PACKAGE_NAME)
    node_count = int(config['env']['DATA_COUNT']) + 1
    config['env']['DATA_COUNT'] = str(node_count)
    cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    check_running(DEFAULT_TASK_COUNT + 1)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'data', data_ids)
