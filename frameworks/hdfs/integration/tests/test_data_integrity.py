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
TEST_FILE_NAME = "test_1"

def setup_module(module):
    uninstall()
    install()
    check_health()


def teardown_module(module):
    uninstall()


@pytest.mark.data_integrity
def test_integrity_on_data_node_failure():
    # give a chance for the name to be resolvable
    time.sleep(30)

    write_data_to_hdfs("data-0-node.hdfs.mesos")

    # gives chance for write to succeed and replication to occur
    time.sleep(5)

    kill_task_with_pattern("DataNode", 'data-0-node.hdfs.mesos')
    kill_task_with_pattern("DataNode", 'data-1-node.hdfs.mesos')

    read_data_from_hdfs("data-2-node.hdfs.mesos")


'''
The first name node (name-0-node) is the active name node by default when HDFS gets installed.
The point of this test is to make sure there's automatic failover such that
the second name node transitions to active when the first name node fails.
'''
@pytest.mark.skip(reason="WIP")
@pytest.mark.data_integrity
def test_integrity_on_name_node_failure():
    # give a chance for the name to be resolvable
    time.sleep(30)

    kill_task_with_pattern("NameNode", 'name-0-node.hdfs.mesos')

    write_data_to_hdfs("data-0-node.hdfs.mesos")

    # gives chance for write to succeed and replication to occur
    time.sleep(5)

    read_data_from_hdfs("data-2-node.hdfs.mesos")

def write_data_to_hdfs(host, content_to_write=TEST_CONTENT_SMALL, filename=TEST_FILE_NAME):
    write_command = """cd $(find /var/lib/mesos/slave/slaves/ -type d -name hadoop-2.6.0-cdh5.7.1 | grep 'data__')
        export JAVA_HOME=../jre1.8.0_112 &&
        echo '{}' | ./bin/hdfs dfs -put - /{}""".format(content_to_write, filename)

    rc, _ = shakedown.run_command_on_agent(host, write_command)
    # rc being True is effectively it being 0...
    assert rc == True
    

def read_data_from_hdfs(host, filename=TEST_FILE_NAME):
    read_command = """cd $(find /var/lib/mesos/slave/slaves/ -type d -name hadoop-2.6.0-cdh5.7.1 | grep 'data__')
        export JAVA_HOME=../jre1.8.0_112 &&
        ./bin/hdfs dfs -cat /{}""".format(filename)

    rc, output = shakedown.run_command_on_agent(host, read_command)
    assert rc == True
    assert output.rstrip() == TEST_CONTENT_SMALL

