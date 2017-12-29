import json
import os
import retrying
import shakedown

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_plan
import sdk_tasks
import sdk_utils

PACKAGE_NAME = 'beta-hdfs'
SERVICE_NAME = 'hdfs'
FOLDERED_SERVICE_NAME = sdk_utils.get_foldered_name(SERVICE_NAME)
FOLDERED_DNS_NAME = sdk_hosts.get_foldered_dns_name(SERVICE_NAME)

DEFAULT_TASK_COUNT = 10  # 3 data nodes, 3 journal nodes, 2 name nodes, 2 zkfc nodes

TEST_CONTENT_SMALL = "This is some test data"
# use long-read alignments to human chromosome 1 as large file input (11GB)
TEST_CONTENT_LARGE_SOURCE = "http://s3.amazonaws.com/nanopore-human-wgs/chr1.sorted.bam"
TEST_FILE_1_NAME = "test_1"
TEST_FILE_2_NAME = "test_2"
DEFAULT_HDFS_TIMEOUT = 5 * 60
HDFS_POD_TYPES = {"journal", "name", "data"}
DOCKER_IMAGE_NAME = "nvaziri/hdfs-client:dev"
KEYTAB = "hdfs.keytab"
CLIENT_PRINCIPALS = {
    "hdfs": "hdfs@{}".format(sdk_auth.REALM),
    "alice": "alice@{}".format(sdk_auth.REALM),
    "bob": "bob@{}".format(sdk_auth.REALM)
}


def get_kerberized_hdfs_client_app():
    app_def_path = "{current_dir}/../tools/{client_id}".format(
        current_dir=os.path.dirname(os.path.realpath(__file__)),
        client_id="hdfsclient.json"
    )
    with open(app_def_path) as f:
        app_def = json.load(f)

    return app_def


def hdfs_command(command):
    return "./bin/hdfs dfs -{}".format(command)


def hdfs_write_command(content_to_write, filename):
    return "echo {} | ./bin/hdfs dfs -put - {}".format(content_to_write, filename)


def write_data_to_hdfs(service_name, filename, content_to_write=TEST_CONTENT_SMALL):
    rc, _ = run_hdfs_command(service_name, hdfs_write_command(content_to_write, filename))
    # rc being True is effectively it being 0...
    return rc


def hdfs_read_command(filename):
    return "./bin/hdfs dfs -cat {}".format(filename)


def read_data_from_hdfs(service_name, filename):
    rc, output = run_hdfs_command(service_name, hdfs_read_command(filename))
    return rc and output.rstrip() == TEST_CONTENT_SMALL


def hdfs_delete_file_command(filename):
    return "./bin/hdfs dfs -rm /{}".format(filename)


def delete_data_from_hdfs(service_name, filename):
    rc, output = run_hdfs_command(service_name, hdfs_delete_file_command(filename))
    return rc


def write_lots_of_data_to_hdfs(service_name, filename):
    write_command = "wget {} -qO- | ./bin/hdfs dfs -put /{}".format(TEST_CONTENT_LARGE_SOURCE, filename)
    rc, output = run_hdfs_command(service_name, write_command)
    return rc


def get_active_name_node(service_name):
    name_node_0_status = get_name_node_status(service_name, "name-0-node")
    if name_node_0_status == "active":
        return "name-0-node"

    name_node_1_status = get_name_node_status(service_name, "name-1-node")
    if name_node_1_status == "active":
        return "name-1-node"

    raise Exception("Failed to determine active name node")


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_HDFS_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def get_name_node_status(service_name, name_node):
    rc, output = run_hdfs_command(service_name, "./bin/hdfs haadmin -getServiceState {}".format(name_node))
    if not rc:
        return rc

    return output.strip()


def run_hdfs_command(service_name, command):
    """
    Execute the command using the Docker client
    """
    full_command = 'docker run -e HDFS_SERVICE_NAME={service_name} {image_name} /bin/bash -c "/configure-hdfs.sh && {cmd}"'.format(
        service_name=service_name,
        image_name=DOCKER_IMAGE_NAME,
        cmd=command
    )

    rc, output = shakedown.run_command_on_master(full_command)
    return rc, output


def check_healthy(service_name, count=DEFAULT_TASK_COUNT, recovery_expected=False):
    sdk_plan.wait_for_completed_deployment(service_name, timeout_seconds=25 * 60)
    if recovery_expected:
        # TODO(elezar): See INFINITY-2109 where we need to better handle recovery health checks
        sdk_plan.wait_for_kicked_off_recovery(service_name, timeout_seconds=25 * 60)
    sdk_plan.wait_for_completed_recovery(service_name, timeout_seconds=25 * 60)
    sdk_tasks.check_running(service_name, count)


def expect_recovery(service_name):
    # TODO(elezar, nima) check_healthy also check for complete deployment, and this should not
    # affect the state of recovery.
    check_healthy(service_name=service_name, count=DEFAULT_TASK_COUNT, recovery_expected=True)


def get_pod_type_instances(pod_type_prefix, service_name=SERVICE_NAME):
    pod_types = sdk_cmd.svc_cli(PACKAGE_NAME, service_name, 'pod list', json=True)
    return [pod_type for pod_type in pod_types if pod_type.startswith(pod_type_prefix)]
