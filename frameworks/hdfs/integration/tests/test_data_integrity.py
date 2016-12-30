import shakedown
import pytest 
import time

from tests.test_utils import (
    PACKAGE_NAME,
    install,
    uninstall,
    check_health,
    kill_task_with_pattern
)

TEST_CONTENT_SMALL = "This is some test data"
# TEST_CONTENT_LARGE = Give a large file as input to the write/read commands...
TEST_FILE_1_NAME = "test_1"
TEST_FILE_2_NAME = "test_2"
HDFS_CMD_TIMEOUT_SEC = 60


def setup_module(module):
    #uninstall()
    #install()
    check_health()


def teardown_module(module):
    #uninstall()
    shakedown.wait_for(lambda: delete_data_from_hdfs("data-0-node.hdfs.mesos", TEST_FILE_1_NAME), HDFS_CMD_TIMEOUT_SEC)
    shakedown.wait_for(lambda: delete_data_from_hdfs("data-0-node.hdfs.mesos", TEST_FILE_2_NAME), HDFS_CMD_TIMEOUT_SEC)


@pytest.mark.data_integrity
def test_integrity_on_data_node_failure():
    shakedown.wait_for(lambda: write_data_to_hdfs("data-0-node.hdfs.mesos", TEST_FILE_1_NAME), HDFS_CMD_TIMEOUT_SEC)

    # gives chance for write to succeed and replication to occur
    time.sleep(5)

    kill_task_with_pattern("DataNode", 'data-0-node.hdfs.mesos')
    kill_task_with_pattern("DataNode", 'data-1-node.hdfs.mesos')
    time.sleep(1)  # give DataNode a chance to die

    shakedown.wait_for(lambda: read_data_from_hdfs("data-2-node.hdfs.mesos", TEST_FILE_1_NAME), HDFS_CMD_TIMEOUT_SEC)

    check_health()


@pytest.mark.data_integrity
def test_integrity_on_name_node_failure():
    """
    The first name node (name-0-node) is the active name node by default when HDFS gets installed.
    This test checks that it is possible to write and read data after the first name node fails.
    """
    kill_task_with_pattern("NameNode", 'name-0-node.hdfs.mesos')
    time.sleep(1)  # give NameNode a chance to die

    shakedown.wait_for(lambda: write_data_to_hdfs("data-0-node.hdfs.mesos", TEST_FILE_2_NAME), HDFS_CMD_TIMEOUT_SEC)

    shakedown.wait_for(lambda: read_data_from_hdfs("data-2-node.hdfs.mesos", TEST_FILE_2_NAME), HDFS_CMD_TIMEOUT_SEC)

    check_health()


def write_data_to_hdfs(host, filename, content_to_write=TEST_CONTENT_SMALL):
    write_command = "echo '{}' | ./bin/hdfs dfs -put - /{}".format(content_to_write, filename)
    rc, _ = run_hdfs_command(host, write_command)
    # rc being True is effectively it being 0...
    return rc


def read_data_from_hdfs(host, filename):
    read_command = "./bin/hdfs dfs -cat /{}".format(filename)
    rc, output = run_hdfs_command(host, read_command)
    return rc and output.rstrip() == TEST_CONTENT_SMALL


def delete_data_from_hdfs(host, filename):
    delete_command = "./bin/hdfs dfs -rm -r /{}".format(filename)
    rc, _ = run_hdfs_command(host, delete_command)
    return rc


def run_hdfs_command(host, command):
    """
    Goes into the hdfs directory, sets JAVA_HOME, and executes the command.
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
    print("java_home: {}".format(java_home))
    return java_home
