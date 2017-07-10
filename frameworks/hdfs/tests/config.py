import shakedown

import sdk_plan as plan
import sdk_utils as utils
import sdk_tasks as tasks

PACKAGE_NAME = 'hdfs'
FOLDERED_SERVICE_NAME = utils.get_foldered_name(PACKAGE_NAME)
DEFAULT_TASK_COUNT = 10  # 3 data nodes, 3 journal nodes, 2 name nodes, 2 zkfc nodes

ZK_SERVICE_PATH = utils.get_zk_path(PACKAGE_NAME)
TEST_CONTENT_SMALL = "This is some test data"
# use long-read alignments to human chromosome 1 as large file input (11GB)
TEST_CONTENT_LARGE_SOURCE = "http://s3.amazonaws.com/nanopore-human-wgs/chr1.sorted.bam"
TEST_FILE_1_NAME = "test_1"
TEST_FILE_2_NAME = "test_2"
DEFAULT_HDFS_TIMEOUT = 5 * 60
HDFS_POD_TYPES = {"journal", "name", "data"}


def write_some_data(data_node_host, file_name):
    shakedown.wait_for(lambda: write_data_to_hdfs(data_node_host, file_name), timeout_seconds=DEFAULT_HDFS_TIMEOUT)


def read_some_data(data_node_host, file_name):
    shakedown.wait_for(lambda: read_data_from_hdfs(data_node_host, file_name), timeout_seconds=DEFAULT_HDFS_TIMEOUT)


def write_data_to_hdfs(data_node_host, filename, content_to_write=TEST_CONTENT_SMALL):
    write_command = "echo '{}' | ./bin/hdfs dfs -put - /{}".format(content_to_write, filename)
    rc, _ = run_hdfs_command(data_node_host, write_command)
    # rc being True is effectively it being 0...
    return rc


def read_data_from_hdfs(data_node_host, filename):
    read_command = "./bin/hdfs dfs -cat /{}".format(filename)
    rc, output = run_hdfs_command(data_node_host, read_command)
    return rc and output.rstrip() == TEST_CONTENT_SMALL


def delete_data_from_hdfs(data_node_host, filename):
    delete_command = "./bin/hdfs dfs -rm /{}".format(filename)
    rc, output = run_hdfs_command(data_node_host, delete_command)
    return rc


def write_lots_of_data_to_hdfs(data_node_host, filename):
    write_command = "wget {} -qO- | ./bin/hdfs dfs -put /{}".format(TEST_CONTENT_LARGE_SOURCE, filename)
    rc, output = run_hdfs_command(data_node_host,write_command)
    return rc


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
    utils.out("java_home: {}".format(java_home))
    return java_home


def check_healthy(count=DEFAULT_TASK_COUNT):
    plan.wait_for_completed_deployment(PACKAGE_NAME, timeout_seconds=25 * 60)
    plan.wait_for_completed_recovery(PACKAGE_NAME, timeout_seconds=25 * 60)
    tasks.check_running(PACKAGE_NAME, count)
