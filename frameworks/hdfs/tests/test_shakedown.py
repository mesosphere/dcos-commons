import json
import time
import traceback

import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_spin as spin
import sdk_tasks as tasks
import sdk_utils
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

TEST_CONTENT_SMALL = "This is some test data"
# TODO: TEST_CONTENT_LARGE = Give a large file as input to the write/read commands...
TEST_FILE_1_NAME = "test_1"
TEST_FILE_2_NAME = "test_2"
HDFS_CMD_TIMEOUT_SEC = 5 * 60
HDFS_POD_TYPES = {"journal", "name", "data"}


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    sdk_utils.gc_frameworks()
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.data_integrity
@pytest.mark.sanity
def test_integrity_on_data_node_failure():
    shakedown.wait_for(lambda: write_data_to_hdfs("data-0-node.hdfs.mesos", TEST_FILE_1_NAME), HDFS_CMD_TIMEOUT_SEC)

    # gives chance for write to succeed and replication to occur
    time.sleep(5)

    tasks.kill_task_with_pattern("DataNode", 'data-0-node.hdfs.mesos')
    tasks.kill_task_with_pattern("DataNode", 'data-1-node.hdfs.mesos')
    time.sleep(1)  # give DataNode a chance to die

    shakedown.wait_for(lambda: read_data_from_hdfs("data-2-node.hdfs.mesos", TEST_FILE_1_NAME), HDFS_CMD_TIMEOUT_SEC)

    check_healthy()


@pytest.mark.data_integrity
@pytest.mark.sanity
def test_integrity_on_name_node_failure():
    """
    The first name node (name-0-node) is the active name node by default when HDFS gets installed.
    This test checks that it is possible to write and read data after the first name node fails.
    """
    tasks.kill_task_with_pattern("NameNode", 'name-0-node.hdfs.mesos')
    time.sleep(1)  # give NameNode a chance to die

    shakedown.wait_for(lambda: write_data_to_hdfs("data-0-node.hdfs.mesos", TEST_FILE_2_NAME), HDFS_CMD_TIMEOUT_SEC)

    shakedown.wait_for(lambda: read_data_from_hdfs("data-2-node.hdfs.mesos", TEST_FILE_2_NAME), HDFS_CMD_TIMEOUT_SEC)

    check_healthy()


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


@pytest.mark.smoke
def test_install():
    check_healthy()


@pytest.mark.sanity
def test_bump_journal_cpus():
    check_healthy()
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')
    sdk_utils.out('journal ids: ' + str(journal_ids))

    marathon.bump_cpu_count_config(PACKAGE_NAME, 'JOURNAL_CPUS')

    tasks.check_tasks_updated(PACKAGE_NAME, 'journal', journal_ids)
    check_healthy()


@pytest.mark.sanity
def test_bump_data_nodes():
    check_healthy()

    data_ids = tasks.get_task_ids(PACKAGE_NAME, 'data')
    sdk_utils.out('data ids: ' + str(data_ids))

    marathon.bump_task_count_config(PACKAGE_NAME, 'DATA_COUNT')

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
    sdk_utils.out('journal ids: ' + str(journal_ids))
    sdk_utils.out('name ids: ' + str(name_ids))
    sdk_utils.out('zkfc ids: ' + str(zkfc_ids))
    sdk_utils.out('data ids: ' + str(data_ids))

    config = marathon.get_config(PACKAGE_NAME)
    sdk_utils.out('marathon config: ')
    sdk_utils.out(config)
    expiry_ms = int(config['env'][app_config_field])
    config['env'][app_config_field] = str(expiry_ms + 1)
    marathon.update_app(PACKAGE_NAME, config)

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
    sdk_utils.out('journal ids: ' + str(journal_ids))
    sdk_utils.out('name ids: ' + str(name_ids))
    sdk_utils.out('zkfc ids: ' + str(zkfc_ids))
    sdk_utils.out('data ids: ' + str(data_ids))

    old_config = marathon.get_config(PACKAGE_NAME)
    config = marathon.get_config(PACKAGE_NAME)
    sdk_utils.out('marathon config: ')
    sdk_utils.out(config)
    expiry_ms = int(config['env'][app_config_field])
    sdk_utils.out('expiry ms: ' + str(expiry_ms))
    config['env'][app_config_field] = str(expiry_ms + 1)
    marathon.update_app(PACKAGE_NAME, config)

    # Wait for journal nodes to be affected by the change
    tasks.check_tasks_updated(PACKAGE_NAME, 'journal', journal_ids)
    journal_ids = tasks.get_task_ids(PACKAGE_NAME, 'journal')

    sdk_utils.out('old config: ')
    sdk_utils.out(old_config)
    # Put the old config back (rollback)
    marathon.update_app(PACKAGE_NAME, old_config)

    # Wait for the journal nodes to return to their old configuration
    tasks.check_tasks_updated(PACKAGE_NAME, 'journal', journal_ids)
    check_healthy()

    config = marathon.get_config(PACKAGE_NAME)
    assert int(config['env'][app_config_field]) == expiry_ms

    # ZKFC and Data tasks should not have been affected
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'zkfc', zkfc_ids)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'data', data_ids)


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


def write_some_data(data_node_host, file_name):
    shakedown.wait_for(lambda: write_data_to_hdfs(data_node_host, file_name), HDFS_CMD_TIMEOUT_SEC)


def read_some_data(data_node_host, file_name):
    shakedown.wait_for(lambda: read_data_from_hdfs(data_node_host, file_name), HDFS_CMD_TIMEOUT_SEC)


def write_data_to_hdfs(data_node_host, filename, content_to_write=TEST_CONTENT_SMALL):
    write_command = "echo '{}' | ./bin/hdfs dfs -put - /{}".format(content_to_write, filename)
    rc, _ = run_hdfs_command(data_node_host, write_command)
    # rc being True is effectively it being 0...
    return rc


def read_data_from_hdfs(data_node_host, filename):
    read_command = "./bin/hdfs dfs -cat /{}".format(filename)
    rc, output = run_hdfs_command(data_node_host, read_command)
    return rc and output.rstrip() == TEST_CONTENT_SMALL


def run_hdfs_command(host, command):
    """
    Go into the Data Node hdfs directory, set JAVA_HOME, and execute the command.
    """
    java_home = find_java_home(host)

    # Find hdfs home directory by looking up the Data Node process.
    # Hdfs directory is found in an arg to the java command.
    hdfs_dir_cmd = """ps -ef | grep hdfs | grep DataNode \
        | awk 'BEGIN {RS=" "}; /-Dhadoop.home.dir/' | sed s/-Dhadoop.home.dir=//"""
    full_command = """cd $({}) &&
        export JAVA_HOME={} &&
        {}""".format(hdfs_dir_cmd, java_home, command)

    rc, output = shakedown.run_command_on_agent(host, full_command)
    return rc, output


def find_java_home(host):
    """
    Find java home by looking up the Data Node process.
    Java home is found in the process command.
    """
    java_home_cmd = """ps -ef | grep hdfs | grep DataNode | grep -v grep \
        | awk '{print $8}' | sed s:/bin/java::"""
    rc, output = shakedown.run_command_on_agent(host, java_home_cmd)
    assert rc
    java_home = output.rstrip()
    sdk_utils.out("java_home: {}".format(java_home))
    return java_home
    
    
def check_healthy(count=DEFAULT_TASK_COUNT):
    service_plan_complete("deploy")
    service_plan_complete("recovery")
    tasks.check_running(PACKAGE_NAME, count)

    
def service_plan_complete(plan_name):
    def fun():
        try:
            pl = service_cli('plan show {}'.format(plan_name))
            sdk_utils.out('Running service_plan_complete for plan {}'.format(plan_name))
            sdk_utils.out(pl)
            sdk_utils.out('status = {}'.format(pl['status']))
            if pl['status'] == 'COMPLETE':
                return True
        except:
            tb = traceback.format_exc()
            sdk_utils.out(tb)
            return False
        sdk_utils.out('Plan {} is not complete ({})'.format(plan_name, pl['status']))
        return False

    return spin.time_wait_return(fun)


def service_cli(cmd_str):
    full_cmd = '{} {}'.format(PACKAGE_NAME, cmd_str)
    ret_str = cmd.run_cli(full_cmd)
    return json.loads(ret_str)

