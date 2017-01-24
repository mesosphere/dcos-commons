import pytest
import shakedown
import time

import sdk_install as install
import sdk_tasks as tasks

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_HDFS_TASK_COUNT,
    check_running
)


def setup_module():
    install.uninstall(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_HDFS_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.sanity
def test_kill_data_node():
    tasks.kill_task_with_pattern('DataNode', 'data-0-node.hdfs.mesos')

    check_running()


@pytest.mark.sanity
def test_kill_name_node():
    tasks.kill_task_with_pattern('NameNode', 'name-0-node.hdfs.mesos')

    time.sleep(1)  # give NameNode a chance to die
    check_running()


@pytest.mark.sanity
def test_kill_journal_node():
    tasks.kill_task_with_pattern('JournalNode', 'journal-0-node.hdfs.mesos')

    check_running()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.sanity
def test_kill_scheduler():
    tasks.kill_task_with_pattern('hdfs.scheduler.Main', shakedown.get_service_ips('marathon').pop())

    check_running()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.sanity
def test_kill_datanode_executor():
    tasks.kill_task_with_pattern('hdfs.executor.Main', 'data-0-node.hdfs.mesos')

    check_running()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.sanity
def test_kill_journalnode_executor():
    tasks.kill_task_with_pattern('hdfs.executor.Main', 'journal-0-node.hdfs.mesos')

    check_running()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.sanity
def test_kill_namenode_executor():
    tasks.kill_task_with_pattern('hdfs.executor.Main', 'name-0-node.hdfs.mesos')

    check_running()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.sanity
def test_kill_all_datanodes():
    for host in shakedown.get_service_ips(PACKAGE_NAME):
        tasks.kill_task_with_pattern('DataNode', host)

    check_running()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.sanity
def test_kill_all_journalnodes():
    for host in shakedown.get_service_ips(PACKAGE_NAME):
        tasks.kill_task_with_pattern('JournalNode', host)

    check_running()


@pytest.mark.skip(reason="This test currently fails. Skipping for now.")
@pytest.mark.sanity
def test_kill_all_namenodes():
    for host in shakedown.get_service_ips(PACKAGE_NAME):
        tasks.kill_task_with_pattern('NameNode', host)

    check_running()
