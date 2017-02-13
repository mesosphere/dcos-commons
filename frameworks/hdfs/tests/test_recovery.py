import pytest
import shakedown
import time

import sdk_cmd as cmd
import sdk_install as install
import sdk_tasks as tasks

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    check_healthy
)


def setup_module():
    install.uninstall(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_journal_node():
    check_healthy()
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal-0')
    name_ids = tasks.get_task_ids(PACKAGE_NAME, 'name')
    zkfc_ids = tasks.get_task_ids(PACKAGE_NAME, 'zkfc')
    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')

    tasks.kill_task_with_pattern('journalnode', 'journal-0-node.hdfs.mesos')
    tasks.check_tasks_updated(PACKAGE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'name', name_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'zkfc', zkfc_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'data', data_ids)
    check_healthy()


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_name_node():
    check_healthy()
    name_ids = tasks.get_task_ids(PACKAGE_NAME, 'name-0')
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    zkfc_ids = tasks.get_task_ids(PACKAGE_NAME, 'zkfc')
    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')

    tasks.kill_task_with_pattern('namenode', 'name-0-node.hdfs.mesos')
    tasks.check_tasks_updated(PACKAGE_NAME, 'name', name_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'zkfc', zkfc_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'data', data_ids)
    check_healthy()


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_data_node():
    check_healthy()
    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data-0')
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    name_ids = tasks.get_task_ids(PACKAGE_NAME, 'name')
    zkfc_ids = tasks.get_task_ids(PACKAGE_NAME, 'zkfc')

    tasks.kill_task_with_pattern('datanode', 'data-0-node.hdfs.mesos')
    tasks.check_tasks_updated(PACKAGE_NAME, 'data', data_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'name', name_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'zkfc', zkfc_ids)
    check_healthy()


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_scheduler():
    check_healthy()
    tasks.kill_task_with_pattern('hdfs.scheduler.Main', shakedown.get_service_ips('marathon').pop())
    check_healthy()


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_journalnodes():
    check_healthy()
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    name_ids = tasks.get_task_ids(PACKAGE_NAME, 'name')
    zkfc_ids = tasks.get_task_ids(PACKAGE_NAME, 'zkfc')
    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')

    for host in shakedown.get_service_ips(PACKAGE_NAME):
        tasks.kill_task_with_pattern('journalnode', host)

    tasks.check_tasks_updated(PACKAGE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'name', name_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'zkfc', zkfc_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'data', data_ids)
    check_healthy()


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_namenodes():
    check_healthy()
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    name_ids = tasks.get_task_ids(PACKAGE_NAME, 'name')
    zkfc_ids = tasks.get_task_ids(PACKAGE_NAME, 'zkfc')
    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')

    for host in shakedown.get_service_ips(PACKAGE_NAME):
        tasks.kill_task_with_pattern('namenode', host)

    tasks.check_tasks_updated(PACKAGE_NAME, 'name', name_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'zkfc', zkfc_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'data', data_ids)
    check_healthy()


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_datanodes():
    check_healthy()
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    name_ids = tasks.get_task_ids(PACKAGE_NAME, 'name')
    zkfc_ids = tasks.get_task_ids(PACKAGE_NAME, 'zkfc')
    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')

    for host in shakedown.get_service_ips(PACKAGE_NAME):
        tasks.kill_task_with_pattern('datanode', host)

    tasks.check_tasks_updated(PACKAGE_NAME, 'data', data_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'name', name_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'zkfc', zkfc_ids)
    check_healthy()


@pytest.mark.sanity
@pytest.mark.recovery
@pytest.mark.special
def test_permanently_replace_namenodes():
    replace_name_node(0)
    replace_name_node(1)
    replace_name_node(0)


def replace_name_node(index):
    check_healthy()
    name_node_name = 'name-' + str(index)
    name_id = tasks.get_task_ids(PACKAGE_NAME, name_node_name)
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    zkfc_ids = tasks.get_task_ids(PACKAGE_NAME, 'zkfc')
    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')

    cmd.run_cli('hdfs pods replace ' + name_node_name)

    tasks.check_tasks_updated(PACKAGE_NAME, name_node_name, name_id)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'data', data_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'zkfc', zkfc_ids)
    check_healthy()
