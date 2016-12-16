import pytest
import shakedown
import os
import time

from tests.test_utils import (
    PACKAGE_NAME,
    check_health,
    install,
    uninstall,
    kill_task_with_pattern
)


def get_scheduler_host():
    return shakedown.get_service_ips('marathon').pop()


def setup_module():
    uninstall()
    install()
    check_health()


def teardown_module(module):
    uninstall()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.recovery
def test_kill_data_node():
    kill_task_with_pattern('DataNode', 'data-0-node.hdfs.mesos')

    check_health()


@pytest.mark.recovery
def test_kill_name_node():
    kill_task_with_pattern('NameNode', 'name-0-node.hdfs.mesos')

    time.sleep(1)  # give NameNode a chance to die
    check_health()


@pytest.mark.recovery
def test_kill_journal_node():
    kill_task_with_pattern('JournalNode', 'journal-0-node.hdfs.mesos')

    check_health()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.recovery
def test_kill_scheduler():
    kill_task_with_pattern('hdfs.scheduler.Main', get_scheduler_host())

    check_health()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.recovery
def test_kill_datanode_executor():
    kill_task_with_pattern('hdfs.executor.Main', 'data-0-node.hdfs.mesos')

    check_health()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.recovery
def test_kill_journalnode_executor():
    kill_task_with_pattern('hdfs.executor.Main', 'journal-0-node.hdfs.mesos')

    check_health()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.recovery
def test_kill_namenode_executor():
    kill_task_with_pattern('hdfs.executor.Main', 'name-0-node.hdfs.mesos')

    check_health()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.recovery
def test_kill_all_datanodes():
    for host in shakedown.get_service_ips(PACKAGE_NAME):
        kill_task_with_pattern('DataNode', host)

    check_health()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.recovery
def test_kill_all_journalnodes():
    for host in shakedown.get_service_ips(PACKAGE_NAME):
        kill_task_with_pattern('JournalNode', host)

    check_health()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.recovery
def test_kill_all_namenodes():
    for host in shakedown.get_service_ips(PACKAGE_NAME):
        kill_task_with_pattern('NameNode', host)

    check_health()
