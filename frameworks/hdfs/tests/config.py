import json
import logging
import os
import retrying
import uuid

import sdk_cmd
import sdk_hosts
import sdk_plan
import sdk_tasks
import sdk_utils

log = logging.getLogger(__name__)

PACKAGE_NAME = sdk_utils.get_package_name("hdfs")
SERVICE_NAME = sdk_utils.get_service_name(PACKAGE_NAME.lstrip("beta-"))
FOLDERED_SERVICE_NAME = sdk_utils.get_foldered_name(SERVICE_NAME)

DEFAULT_TASK_COUNT = 10  # 3 data nodes, 3 journal nodes, 2 name nodes, 2 zkfc nodes

TEST_CONTENT_SMALL = "This is some test data"
DEFAULT_HDFS_TIMEOUT = 5 * 60
HDFS_POD_TYPES = {"journal", "name", "data"}
KEYTAB = "hdfs.keytab"
HADOOP_VERSION = "hadoop-2.6.0-cdh5.9.1"

DOCKER_IMAGE_NAME = "elezar/hdfs-client:dev"
CLIENT_APP_NAME = "hdfs-client"


def get_kerberized_hdfs_client_app():
    app_def_path = "{current_dir}/../tools/docker-client/{client_id}".format(
        current_dir=os.path.dirname(os.path.realpath(__file__)), client_id="hdfsclient.json"
    )
    with open(app_def_path) as f:
        app_def = json.load(f)

    return app_def


def hadoop_command(command):
    return "/{}/bin/hdfs {}".format(HADOOP_VERSION, command)


def hdfs_command(command):
    return hadoop_command("dfs -{}".format(command))


def get_unique_filename(prefix: str) -> str:
    return "{}.{}".format(prefix, str(uuid.uuid4()))


def write_data_to_hdfs(
        filename,
        expect_failure_message=None,
        content_to_write=TEST_CONTENT_SMALL) -> tuple:
    # Custom check: If the command returned an error of "File exists", then assume the write has succeeded.
    # This can happen when e.g. the SSH connection flakes but the data itself was written successfully.
    success, stdout, stderr = run_client_command(
        "echo {} | {}".format(content_to_write, hdfs_command("put - {}".format(filename))),
        success_check=lambda rc, stdout, stderr: rc == 0 or (expect_failure_message and expect_failure_message in stderr) or "File exists" in stderr
    )
    if "File exists" in stderr:
        log.info("Ignoring failure: Looks like the data was successfully written in a previous attempt")
    elif not expect_failure_message:
        assert success, "Failed to write {}: {}".format(filename, stderr)
    return (success, stdout, stderr)


def read_data_from_hdfs(
        filename,
        expect_failure_message=None,
        content_to_verify=TEST_CONTENT_SMALL) -> tuple:
    success, stdout, stderr = run_client_command(
        hdfs_command("cat {}".format(filename)),
        success_check=lambda rc, stdout, stderr: rc == 0 or (expect_failure_message and expect_failure_message in stderr),
    )
    if not expect_failure_message:
        success = success and stdout.rstrip() == content_to_verify
        assert success, "Failed to read {}: {}".format(filename, stderr)
    return (success, stdout, stderr)


def delete_data_from_hdfs(filename) -> tuple:
    return run_client_command(hdfs_command("rm /{}".format(filename)))


def list_files_in_hdfs(filename) -> tuple:
    return run_client_command(hdfs_command("ls {}".format(filename)))


def get_hdfs_client_app(service_name, kerberos=None) -> dict:
    app = {
        "id": CLIENT_APP_NAME,
        "mem": 1024,
        "user": "nobody",
        "container": {
            "type": "MESOS",
            "docker": {"image": DOCKER_IMAGE_NAME, "forcePullImage": True},
        },
        "networks": [{"mode": "host"}],
        "env": {
            "JAVA_HOME": "/usr/lib/jvm/default-java",
            "KRB5_CONFIG": "/etc/krb5.conf",
            # for foldered name in test_kerberos_auth.py:
            "HDFS_SERVICE_NAME": sdk_hosts._safe_name(service_name),
            "HADOOP_VERSION": HADOOP_VERSION,
        },
    }

    if kerberos:
        # Insert kerberos-related configuration into the client:
        app["env"]["REALM"] = kerberos.get_realm()
        app["env"]["KDC_ADDRESS"] = kerberos.get_kdc_address()
        app["secrets"] = {
            "hdfs_keytab": {
                "source": kerberos.get_keytab_path()
            }
        }
        app["container"]["volumes"] = [
            {
                "containerPath": "/{}/hdfs.keytab".format(HADOOP_VERSION),
                "secret": "hdfs_keytab"
            }
        ]

    return app


def run_client_command(hdfs_command, success_check=lambda rc, stdout, stderr: rc == 0):
    """
    Execute the command (provided as args to the 'hdfs' binary) using the Docker client
    """
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=DEFAULT_HDFS_TIMEOUT * 1000,
        retry_on_result=lambda res: not res[0],
    )
    def _run_hdfs_command():
        rc, stdout, stderr = sdk_cmd.marathon_task_exec(CLIENT_APP_NAME, "/bin/bash -c '{}'".format(hdfs_command))
        return (success_check(rc, stdout, stderr), stdout, stderr)

    return _run_hdfs_command()


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
    rc, stdout, _ = sdk_cmd.svc_cli(PACKAGE_NAME, service_name, "pod list")
    assert rc == 0
    return [pod_type for pod_type in json.loads(stdout) if pod_type.startswith(pod_type_prefix)]
