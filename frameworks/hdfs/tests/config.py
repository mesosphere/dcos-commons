import json
import logging
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

DOCKER_IMAGE_NAME = "mesosphere/hdfs-testing-client:6972ea3833c9449111aceaa998e3e093a9c8dcee"
CLIENT_APP_NAME = "hdfs-client"


def hadoop_command(command):
    return "/{}/bin/hdfs {}".format(HADOOP_VERSION, command)


def hdfs_command(command):
    return hadoop_command("dfs -{}".format(command))


def get_unique_filename(prefix: str) -> str:
    return "{}.{}".format(prefix, str(uuid.uuid4()))


def hdfs_client_write_data(
        filename,
        expect_failure_message=None,
        content_to_write=TEST_CONTENT_SMALL,
) -> tuple:
    def success_check(rc, stdout, stderr):
        if rc == 0 and not stderr:
            # rc is still zero even if the "put" command failed! This is because "task exec" eats the return code.
            # Therefore we must also check stderr to tell if the command actually succeeded.
            # In practice, stderr is empty when a "put" operation has succeeded.
            return True
        elif expect_failure_message and expect_failure_message in stderr:
            # The expected failure message has occurred. Stop trying.
            return True
        elif "File exists" in stderr:
            # If the command returned an error of "File exists", then assume the write had succeeded on a previous run.
            # This can happen when e.g. the write succeeded in a previous attempt, but then the connection flaked, or
            # if hdfs had previously successfully completed the write when also outputting some warning on stderr.
            log.info("Ignoring failure: Looks like the data was successfully written in a previous attempt")
            return True
        elif "but this CLI only supports" in stderr:
            # Ignore warnings about CLI being outdated compared to DC/OS version
            return True
        else:
            # Try again
            return False

    success, stdout, stderr = run_client_command(
        "echo {} | {}".format(content_to_write, hdfs_command("put - {}".format(filename))),
        success_check=success_check,
    )
    assert success, "Failed to write {}: {}".format(filename, stderr)
    return (success, stdout, stderr)


def hdfs_client_read_data(
        filename,
        expect_failure_message=None,
        content_to_verify=TEST_CONTENT_SMALL,
) -> tuple:
    def success_check(rc, stdout, stderr):
        if rc == 0 and stdout.rstrip() == content_to_verify:
            # rc only tells us if the 'task exec' operation itself failed. It is zero when the hdfs command fails.
            # This is because "task exec" eats that return code.
            # However, we CANNOT just check for an empty stderr here because hdfs can put superfluous warnings there even when the operation succeeds.
            # So to determine success, we just directly check that the content of stdout matches what we're looking for.
            # Example stderr garbage:
            #   18/08/22 02:43:28 WARN shortcircuit.DomainSocketFactory: error creating DomainSocket
            #   java.net.ConnectException: connect(2) error: No such file or directory when trying to connect to 'dn_socket'
            #   ...
            return True
        elif expect_failure_message and expect_failure_message in stderr:
            # The expected failure message has occurred. Stop trying.
            return True
        else:
            # Try again
            return False

    success, stdout, stderr = run_client_command(
        hdfs_command("cat {}".format(filename)),
        success_check=success_check,
    )
    assert success, "Failed to read {}, or content didn't match expected value '{}': {}".format(filename, content_to_verify, stderr)
    return (success, stdout, stderr)


def hdfs_client_list_files(filename) -> tuple:
    return run_client_command(hdfs_command("ls {}".format(filename)))


def get_hdfs_client_app(service_name, kerberos=None) -> dict:
    """
    Returns a Marathon app definition for an HDFS client against the specified service.

    This app should be installed AFTER the service is up and running, or else it may fail with an error like:

    18/08/21 20:36:57 FATAL conf.Configuration: error parsing conf core-site.xml
           org.xml.sax.SAXParseException; Premature end of file.
    """
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
    Execute the provided shell command within the HDFS Docker client.
    Client app must have first been installed to marathon, see using get_hdfs_client_app().
    """
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=DEFAULT_HDFS_TIMEOUT * 1000,
        retry_on_result=lambda res: not res[0],
    )
    def _run_client_command():
        rc, stdout, stderr = sdk_cmd.marathon_task_exec(CLIENT_APP_NAME, "/bin/bash -c '{}'".format(hdfs_command))
        return (success_check(rc, stdout, stderr), stdout, stderr)

    return _run_client_command()


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
    _, stdout, _ = sdk_cmd.svc_cli(PACKAGE_NAME, service_name, "pod list", check=True)
    return [pod_type for pod_type in json.loads(stdout) if pod_type.startswith(pod_type_prefix)]
