import pytest
import time
import xml.etree.ElementTree as etree

import shakedown

import sdk_cmd as cmd
import sdk_hosts as hosts
import sdk_install as install
import sdk_marathon as marathon
import sdk_plan as plan
import sdk_tasks as tasks
import sdk_utils as utils
from tests.config import *


def setup_module(module):
    install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)
    utils.gc_frameworks()
    install.install(
        PACKAGE_NAME,
        DEFAULT_TASK_COUNT,
        service_name=FOLDERED_SERVICE_NAME,
        additional_options={"service": { "name": FOLDERED_SERVICE_NAME } })
    plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME)


def setup_function(function):
    check_healthy()


def teardown_module(module):
    install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)

@pytest.mark.sanity
def test_endpoints():
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    core_site = etree.fromstring(cmd.run_cli('hdfs --name={} endpoints core-site.xml'.format(FOLDERED_SERVICE_NAME)))
    check_properties(core_site, {
        'ha.zookeeper.parent-znode': '/dcos-service-test__integration__hdfs/hadoop-ha'
    })

    hdfs_site = etree.fromstring(cmd.run_cli('hdfs --name={} endpoints hdfs-site.xml'.format(FOLDERED_SERVICE_NAME)))
    expect = {
        'dfs.namenode.shared.edits.dir': 'qjournal://' + ';'.join([
            hosts.autoip_host(FOLDERED_SERVICE_NAME, 'journal-{}-node'.format(i), 8485) for i in range(3)]) + '/hdfs',
    }
    for i in range(2):
        expect['dfs.namenode.rpc-address.hdfs.name-{}-node'.format(i)] = hosts.autoip_host(FOLDERED_SERVICE_NAME, 'name-{}-node'.format(i), 9001)
        expect['dfs.namenode.http-address.hdfs.name-{}-node'.format(i)] = hosts.autoip_host(FOLDERED_SERVICE_NAME, 'name-{}-node'.format(i), 9002)
    check_properties(hdfs_site, expect)


def check_properties(xml, expect):
    found = {}
    for prop in xml.findall('property'):
        name = prop.find('name').text
        if name in expect:
            found[name] = prop.find('value').text
    utils.out('expect: {}\nfound:  {}'.format(expect, found))
    assert expect == found


@pytest.mark.skip(reason="HDFS-451")
@pytest.mark.data_integrity
@pytest.mark.sanity
def test_integrity_on_data_node_failure():
    write_some_data('data-0-node', TEST_FILE_1_NAME)

    # gives chance for write to succeed and replication to occur
    time.sleep(9)

    tasks.kill_task_with_pattern("DataNode", hosts.system_host(FOLDERED_SERVICE_NAME, 'data-0-node'))
    tasks.kill_task_with_pattern("DataNode", hosts.system_host(FOLDERED_SERVICE_NAME, 'data-1-node'))
    time.sleep(1)  # give DataNode a chance to die

    read_some_data('data-2-node', TEST_FILE_1_NAME)

    check_healthy()


@pytest.mark.skip(reason="HDFS-451")
@pytest.mark.data_integrity
@pytest.mark.sanity
def test_integrity_on_name_node_failure():
    """
    The first name node (name-0-node) is the active name node by default when HDFS gets installed.
    This test checks that it is possible to write and read data after the first name node fails.
    """
    tasks.kill_task_with_pattern("NameNode", hosts.system_host(FOLDERED_SERVICE_NAME, 'name-0-node'))
    time.sleep(1)  # give NameNode a chance to die

    write_some_data('data-0-node', TEST_FILE_2_NAME)

    read_some_data('data-2-node', TEST_FILE_2_NAME)

    check_healthy()


@pytest.mark.recovery
def test_kill_journal_node():
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal-0')
    name_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    tasks.kill_task_with_pattern('journalnode', hosts.system_host(FOLDERED_SERVICE_NAME, 'journal-0-node'))
    check_healthy()
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_name_node():
    name_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-0')
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    tasks.kill_task_with_pattern('namenode', hosts.system_host(FOLDERED_SERVICE_NAME, 'name-0-node'))
    check_healthy()
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_data_node():
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data-0')
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')

    tasks.kill_task_with_pattern('datanode', hosts.system_host(FOLDERED_SERVICE_NAME, 'data-0-node'))
    check_healthy()
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_scheduler():
    tasks.kill_task_with_pattern('hdfs.scheduler.Main', shakedown.get_service_ips('marathon').pop())
    check_healthy()


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_journalnodes():
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    for host in shakedown.get_service_ips(FOLDERED_SERVICE_NAME):
        tasks.kill_task_with_pattern('journalnode', host)

    check_healthy()
    # name nodes fail and restart, so don't check those
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_namenodes():
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    for host in shakedown.get_service_ips(FOLDERED_SERVICE_NAME):
        tasks.kill_task_with_pattern('namenode', host)

    check_healthy()
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_datanodes():
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    for host in shakedown.get_service_ips(FOLDERED_SERVICE_NAME):
        tasks.kill_task_with_pattern('datanode', host)

    check_healthy()
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanently_replace_namenodes():
    replace_name_node(0)
    replace_name_node(1)
    replace_name_node(0)


@pytest.mark.sanity
@pytest.mark.recovery
def test_permanent_and_transient_namenode_failures_0_1():
    check_healthy()
    name_0_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-0')
    name_1_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-1')
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    cmd.run_cli('hdfs --name={} pods replace name-0'.format(FOLDERED_SERVICE_NAME))
    cmd.run_cli('hdfs --name={} pods restart name-1'.format(FOLDERED_SERVICE_NAME))

    check_healthy()
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-0', name_0_ids)
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-1', name_1_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)

@pytest.mark.sanity
@pytest.mark.recovery
def test_permanent_and_transient_namenode_failures_1_0():
    check_healthy()
    name_0_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-0')
    name_1_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name-1')
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    cmd.run_cli('hdfs --name={} pods replace name-1'.format(FOLDERED_SERVICE_NAME))
    cmd.run_cli('hdfs --name={} pods restart name-0'.format(FOLDERED_SERVICE_NAME))

    check_healthy()
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-0', name_0_ids)
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name-1', name_1_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.smoke
def test_install():
    check_healthy()


@pytest.mark.sanity
def test_bump_journal_cpus():
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    utils.out('journal ids: ' + str(journal_ids))

    marathon.bump_cpu_count_config(FOLDERED_SERVICE_NAME, 'JOURNAL_CPUS')

    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    check_healthy()


@pytest.mark.sanity
def test_bump_data_nodes():
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')
    utils.out('data ids: ' + str(data_ids))

    marathon.bump_task_count_config(FOLDERED_SERVICE_NAME, 'DATA_COUNT')

    check_healthy(DEFAULT_TASK_COUNT + 1)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


@pytest.mark.sanity
def test_modify_app_config():
    old_recovery_plan = plan.get_recovery_plan(PACKAGE_NAME).json()

    app_config_field = 'TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS'
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    name_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'name')

    config = marathon.get_config(FOLDERED_SERVICE_NAME)
    utils.out('marathon config: ')
    utils.out(config)
    expiry_ms = int(config['env'][app_config_field])
    config['env'][app_config_field] = str(expiry_ms + 1)
    marathon.update_app(FOLDERED_SERVICE_NAME, config)

    # All tasks should be updated because hdfs-site.xml has changed
    check_healthy()
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'name', name_ids)
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'data', journal_ids)

    new_recovery_plan = plan.get_recovery_plan(PACKAGE_NAME).json()
    # Recovery should not have been modified at all
    assert(old_recovery_plan_status == new_recovery_plan_status)


@pytest.mark.sanity
def test_modify_app_config_rollback():
    app_config_field = 'TASKCFG_ALL_CLIENT_READ_SHORTCIRCUIT_STREAMS_CACHE_SIZE_EXPIRY_MS'

    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    old_config = marathon.get_config(FOLDERED_SERVICE_NAME)
    config = marathon.get_config(FOLDERED_SERVICE_NAME)
    utils.out('marathon config: ')
    utils.out(config)
    expiry_ms = int(config['env'][app_config_field])
    utils.out('expiry ms: ' + str(expiry_ms))
    config['env'][app_config_field] = str(expiry_ms + 1)
    marathon.update_app(FOLDERED_SERVICE_NAME, config)

    # Wait for journal nodes to be affected by the change
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')

    utils.out('old config: ')
    utils.out(old_config)
    # Put the old config back (rollback)
    marathon.update_app(FOLDERED_SERVICE_NAME, old_config)

    # Wait for the journal nodes to return to their old configuration
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    check_healthy()

    config = marathon.get_config(FOLDERED_SERVICE_NAME)
    assert int(config['env'][app_config_field]) == expiry_ms

    # Data tasks should not have been affected
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


def replace_name_node(index):
    check_healthy()
    name_node_name = 'name-' + str(index)
    name_id = tasks.get_task_ids(FOLDERED_SERVICE_NAME, name_node_name)
    journal_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'journal')
    data_ids = tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'data')

    cmd.run_cli('hdfs --name={} pods replace {}'.format(FOLDERED_SERVICE_NAME, name_node_name))

    check_healthy()
    tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, name_node_name, name_id)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'journal', journal_ids)
    tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, 'data', data_ids)


def write_some_data(data_node_name, file_name):
    def write_data_to_hdfs():
        write_command = "echo '{}' | ./bin/hdfs dfs -put - /{}".format(TEST_CONTENT_SMALL, file_name)
        rc, _ = run_hdfs_command(data_node_name, write_command)
        # rc being True is effectively it being 0...
        return rc
    shakedown.wait_for(lambda: write_data_to_hdfs(), timeout_seconds=HDFS_CMD_TIMEOUT_SEC)


def read_some_data(data_node_name, file_name):
    def read_data_from_hdfs():
        read_command = "./bin/hdfs dfs -cat /{}".format(file_name)
        rc, output = run_hdfs_command(data_node_name, read_command)
        return rc and output.rstrip() == TEST_CONTENT_SMALL
    shakedown.wait_for(lambda: read_data_from_hdfs(), timeout_seconds=HDFS_CMD_TIMEOUT_SEC)


def run_hdfs_command(task_name, command):
    """
    Go into the Data Node hdfs directory, set JAVA_HOME, and execute the command.
    """
    host = hosts.system_host(FOLDERED_SERVICE_NAME, task_name)
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
    utils.out("java_home: {}".format(java_home))
    return java_home


def check_healthy(count=DEFAULT_TASK_COUNT):
    plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME, timeout_seconds=20 * 60)
    plan.wait_for_completed_recovery(FOLDERED_SERVICE_NAME, timeout_seconds=20 * 60)
    tasks.check_running(FOLDERED_SERVICE_NAME, count)
