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
    check_healthy
)

HDFS_POD_TYPES = {"journal", "name", "data"}


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_bump_journal_cpus():
    check_healthy()
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    print('journal ids: ' + str(journal_ids))

    config = marathon.get_config(PACKAGE_NAME)
    print('marathon config: ')
    print(config)
    cpus = float(config['env']['JOURNAL_CPUS'])
    config['env']['JOURNAL_CPUS'] = str(cpus + 0.1)
    cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    tasks.check_tasks_updated(PACKAGE_NAME, 'journal', journal_ids)
    check_healthy()


@pytest.mark.sanity
def test_bump_data_nodes():
    check_healthy()

    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')
    print('data ids: ' + str(data_ids))

    config = marathon.get_config(PACKAGE_NAME)
    node_count = int(config['env']['DATA_COUNT']) + 1
    config['env']['DATA_COUNT'] = str(node_count)
    cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    check_healthy(DEFAULT_TASK_COUNT + 1)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'data', data_ids)


@pytest.mark.sanity
def test_modify_app_config():
    check_healthy()
    app_config_field = 'TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS'

    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    name_ids = tasks.get_task_ids(PACKAGE_NAME, 'name')
    zkfc_ids = tasks.get_task_ids(PACKAGE_NAME, 'zkfc')
    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')
    print('journal ids: ' + str(journal_ids))
    print('name ids: ' + str(name_ids))
    print('zkfc ids: ' + str(zkfc_ids))
    print('data ids: ' + str(data_ids))

    config = marathon.get_config(PACKAGE_NAME)
    print('marathon config: ')
    print(config)
    expiry_ms = int(config['env'][app_config_field])
    config['env'][app_config_field] = str(expiry_ms + 1)
    r = cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    # All tasks should be updated because hdfs-site.xml has changed
    tasks.check_tasks_updated(PACKAGE_NAME, 'journal', journal_ids)
    tasks.check_tasks_updated(PACKAGE_NAME, 'name', name_ids)
    tasks.check_tasks_updated(PACKAGE_NAME, 'zkfc', zkfc_ids)
    tasks.check_tasks_updated(PACKAGE_NAME, 'data', journal_ids)

    check_healthy()


@pytest.mark.sanity
def test_modify_app_config_rollback():
    check_healthy()
    app_config_field = 'TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS'

    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    name_ids = tasks.get_task_ids(PACKAGE_NAME, 'name')
    zkfc_ids = tasks.get_task_ids(PACKAGE_NAME, 'zkfc')
    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')
    print('journal ids: ' + str(journal_ids))
    print('name ids: ' + str(name_ids))
    print('zkfc ids: ' + str(zkfc_ids))
    print('data ids: ' + str(data_ids))

    old_config = marathon.get_config(PACKAGE_NAME)
    config = marathon.get_config(PACKAGE_NAME)
    print('marathon config: ')
    print(config)
    expiry_ms = int(config['env'][app_config_field])
    print('expiry ms: ' + str(expiry_ms))
    config['env'][app_config_field] = str(expiry_ms + 1)
    r = cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    # Wait for journal nodes to be affected by the change
    tasks.check_tasks_updated(PACKAGE_NAME, 'journal', journal_ids)
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')

    print('old config: ')
    print(old_config)
    # Put the old config back (rollback)
    r = cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=old_config)

    # Wait for the journal nodes to return to their old configuration
    tasks.check_tasks_updated(PACKAGE_NAME, 'journal', journal_ids)
    check_healthy()

    config = marathon.get_config(PACKAGE_NAME)
    assert int(config['env'][app_config_field]) == expiry_ms

    # ZKFC and Data tasks should not have been affected
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'zkfc', zkfc_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'data', data_ids)


