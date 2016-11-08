import pytest
import shakedown
import os
import time

from tests.test_utils import (
    check_health,
    uninstall,
)

from tests.defaults import DEFAULT_NODE_COUNT, PACKAGE_NAME


strict_mode = os.getenv('SECURITY', 'permissive')


def get_scheduler_host():
    return shakedown.get_service_ips('marathon').pop()


def kill_task_with_pattern(pattern, host=None):
    command = (
        "sudo kill -9 "
        "$(ps ax | grep {} | grep -v grep | tr -s ' ' | sed 's/^ *//g' | "
        "cut -d ' ' -f 1)".format(pattern)
    )
    if host is None:
        result = shakedown.run_command_on_master(command)
    else:
        result = shakedown.run_command_on_agent(host, command)

    if not result:
        raise RuntimeError(
            'Failed to kill task with pattern "{}"'.format(pattern)
        )


def setup_module():
    uninstall()

    if strict_mode == 'strict':
        shakedown.install_package_and_wait(package_name=PACKAGE_NAME, options_file=os.path.dirname(os.path.abspath(inspect.getfile(inspect.currentframe()))) + "/strict.json")
    else:
        shakedown.install_package_and_wait(package_name=PACKAGE_NAME, options_file=None)

    check_health()


def teardown_module(module):
    uninstall()


@pytest.mark.recovery
def test_kill_data_node():
    kill_task_with_pattern('DataNode', 'datanode-0.hdfs.mesos')

    check_health()


@pytest.mark.recovery
def test_kill_name_node():
    kill_task_with_pattern('NameNode', 'namenode-0.hdfs.mesos')

    time.sleep(1)  # give NameNode a chance to die
    check_health()


@pytest.mark.recovery
def test_kill_journal_node():
    kill_task_with_pattern('JournalNode', 'journalnode-0.hdfs.mesos')

    check_health()


@pytest.mark.recovery
def test_kill_scheduler():
    kill_task_with_pattern('hdfs.scheduler.Main', get_scheduler_host())

    check_health()


# This test currently fails.
#@pytest.mark.recovery
#def test_kill_datanode_executor():
#    kill_task_with_pattern('hdfs.executor.Main', 'datanode-0.hdfs.mesos')
#
#    check_health()


# This test currently fails.
#@pytest.mark.recovery
#def test_kill_journalnode_executor():
#    kill_task_with_pattern('hdfs.executor.Main', 'journalnode-0.hdfs.mesos')
#
#    check_health()


# This test currently fails.
#@pytest.mark.recovery
#def test_kill_namenode_executor():
#    kill_task_with_pattern('hdfs.executor.Main', 'namenode-0.hdfs.mesos')
#
#    check_health()


@pytest.mark.recovery
def test_kill_all_datanodes():
    for host in shakedown.get_service_ips(PACKAGE_NAME):
        kill_task_with_pattern('DataNode', host)

    check_health()


# This test currently fails.
#@pytest.mark.recovery
#def test_kill_all_journalnodes():
#    for host in shakedown.get_service_ips(PACKAGE_NAME):
#        kill_task_with_pattern('JournalNode', host)
#
#    check_health()


@pytest.mark.recovery
def test_kill_all_namenodes():
    for host in shakedown.get_service_ips(PACKAGE_NAME):
        kill_task_with_pattern('NameNode', host)

    check_health()