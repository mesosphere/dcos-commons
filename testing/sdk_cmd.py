"""Utilities relating to running commands and HTTP requests

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_cmd IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import functools
import json as jsonlib
import os
import logging
import requests
import retrying
import subprocess
import tempfile
import time
import urllib.parse

import sdk_utils


log = logging.getLogger(__name__)

DEFAULT_TIMEOUT_SECONDS = 30 * 60
SSH_USERNAME = os.environ.get("DCOS_SSH_USER", "core")


def service_request(
    method,
    service_name,
    service_path,
    retry=True,
    raise_on_error=True,
    log_args=True,
    timeout_seconds=60,
    **kwargs,
):
    """Used to query a service running on the cluster. See `cluster_request()` for arg meanings.
    : param service_name: The name of the service, e.g. 'marathon' or 'hello-world'
    : param service_path: HTTP path to be queried against the service, e.g. '/v2/apps'. Leading slash is optional.
    """
    # Sanitize leading slash on service_path before calling urljoin() to avoid this:
    # 'http://example.com/service/myservice/' + '/v1/rpc' = 'http://example.com/v1/rpc'
    cluster_path = urllib.parse.urljoin(
        "/service/{}/".format(service_name), service_path.lstrip("/")
    )
    return cluster_request(
        method, cluster_path, retry, raise_on_error, log_args, timeout_seconds, **kwargs
    )


def cluster_request(
    method,
    cluster_path,
    retry=True,
    raise_on_error=True,
    log_args=True,
    timeout_seconds=60,
    **kwargs,
):
    """Queries the provided cluster HTTP path using the provided method, with the following handy features:
    - The DCOS cluster's URL is automatically applied to the provided path.
    - Auth headers are automatically added.
    - If the response code is >= 400, optionally retries and / or raises a `requests.exceptions.HTTPError`.

    : param method: Method to use for the query, such as `GET`, `POST`, `DELETE`, or `PUT`.
    : param cluster_path: HTTP path to be queried on the cluster, e.g. `/ marathon / v2 / apps`. Leading slash is optional.
    : param retry: Whether to retry the request automatically if an HTTP error (>= 400) is returned.
    : param raise_on_error: Whether to raise a `requests.exceptions.HTTPError` if the response code is >= 400.
                           Disabling this effectively implies `retry = False` where HTTP status is concerned.
    : param log_args: Whether to log the contents of `kwargs`. Can be disabled to reduce noise.
    : param kwargs: Additional arguments to requests.request(), such as `json = {"example": "content"}`
                   or `params = {"example": "param"}`.
    : rtype: requests.Response
    """

    url = urllib.parse.urljoin(sdk_utils.dcos_url(), cluster_path)
    # consistently include slash prefix for clearer logging below
    cluster_path = "/" + cluster_path.lstrip("/")

    # Wrap token in callback for requests library to invoke:
    class AuthHeader(requests.auth.AuthBase):
        def __init__(self, token):
            self._token = token

        def __call__(self, r):
            r.headers["Authorization"] = "token={}".format(self._token)
            return r

    auth = AuthHeader(sdk_utils.dcos_token())

    def _cluster_request():
        start = time.time()
        response = requests.request(
            method, url, auth=auth, verify=False, timeout=timeout_seconds, **kwargs
        )
        end = time.time()

        log_msg = "(HTTP {}) {}".format(method.upper(), cluster_path)
        if kwargs:
            # log arg content (or just arg names, with hack to avoid 'dict_keys([...])') if present
            log_msg += " (args: {})".format(kwargs if log_args else [e for e in kwargs.keys()])
        log_msg += " => {} ({})".format(
            response.status_code, sdk_utils.pretty_duration(end - start)
        )
        log.info(log_msg)

        if not response.ok:
            # Query failed (>= 400). Before (potentially) throwing, print response payload which may
            # include additional error details.
            response_text = response.text
            if response_text:
                log.info(
                    "Response content ({} bytes):\n{}".format(len(response_text), response_text)
                )
            else:
                log.info("No response content")
        if raise_on_error:
            response.raise_for_status()
        return response

    if retry:
        # Use wrapper to implement retry:
        @retrying.retry(wait_fixed=1000, stop_max_delay=timeout_seconds * 1000)
        def retry_fn():
            return _cluster_request()

        return retry_fn()
    else:
        # No retry, invoke directly:
        return _cluster_request()


def svc_cli(
    package_name,
    service_name,
    service_cmd,
    json=False,
    print_output=True,
    return_stderr_in_stdout=False,
    check=False,
):
    full_cmd = "{} --name={} {}".format(package_name, service_name, service_cmd)

    if not json:
        return run_cli(
            full_cmd,
            print_output=print_output,
            return_stderr_in_stdout=return_stderr_in_stdout,
            check=check,
        )
    else:
        # TODO(elezar): We shouldn't use json=True and return_stderr_in_stdout=True together
        # assert not return_stderr_in_stdout, json=True and return_stderr_in_stdout=True should not be used together
        return get_json_output(full_cmd, print_output=print_output, check=False)


def run_cli(cmd, print_output=True, return_stderr_in_stdout=False, check=False):
    _, stdout, stderr = run_raw_cli(cmd, print_output, check=check)

    if return_stderr_in_stdout:
        return stdout + "\n" + stderr

    return stdout


def run_raw_cli(cmd, print_output=True, check=False):
    """Runs the command with `dcos` as the prefix to the shell command
    and returns a tuple containing exit code, stdout, and stderr.

    eg. `cmd`= "package install <package-name>" results in:
    $ dcos package install < package - name >
    """
    dcos_cmd = "dcos {}".format(cmd)
    log.info("(CLI) {}".format(dcos_cmd))
    return _run_cmd(dcos_cmd, print_output, check)


def _run_cmd(cmd, print_output, check, timeout_seconds=None):
    result = subprocess.run(
        [cmd],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        shell=True,
        check=check,
        timeout=timeout_seconds,
    )

    if result.returncode != 0:
        log.info("Got exit code {} to command: {}".format(result.returncode, cmd))

    if result.stdout:
        stdout = result.stdout.decode("utf-8").strip()
    else:
        stdout = ""

    if result.stderr:
        stderr = result.stderr.decode("utf-8").strip()
    else:
        stderr = ""

    if print_output:
        if stdout:
            log.info("STDOUT:\n{}".format(stdout))
        if stderr:
            log.info("STDERR:\n{}".format(stderr))

    return result.returncode, stdout, stderr


@retrying.retry(
    stop_max_attempt_number=3, wait_fixed=1000, retry_on_result=lambda result: not result
)
def create_task_text_file(marathon_task_name: str, filename: str, lines: list) -> bool:
    # Write file, then validate number of lines in file
    output_cmd = '''bash -c "cat > {output_file} << EOL
{content}
EOL
wc -l {output_file}"'''.format(
        output_file=filename, content="\n".join(lines)
    )
    rc, stdout, stderr = marathon_task_exec(marathon_task_name, output_cmd)

    if rc or stderr:
        log.warning(
            "Error creating file %s. rc=%s stdout=%s stderr=%s", filename, rc, stdout, stderr
        )
        return False

    written_lines = 0
    try:
        written_lines = int(stdout.split(" ")[0])
    except Exception as e:
        log.warning(e)

    expected_lines = len("\n".join(lines).split("\n"))
    if written_lines != expected_lines:
        log.warning(
            "Number of written lines do not match. stdout=%s expected=%s written=%s",
            stdout,
            expected_lines,
            written_lines,
        )
        return False

    return True


@retrying.retry(
    stop_max_attempt_number=3, wait_fixed=1000, retry_on_result=lambda result: not result
)
def kill_task_with_pattern(pattern, agent_host=None, timeout_seconds=DEFAULT_TIMEOUT_SECONDS):
    """SSHes into the leader node (or the provided agent node) and kills any tasks matching the
    provided regex pattern in their command.
    """
    # -f: Patterns may be against arguments in the command itself
    # -o: Kill the oldest command: avoid killing ourself, assuming there's another command that matches the pattern..
    command = "sudo pkill -9 -f -o {}".format(pattern)
    if agent_host is None:
        rc, _, _ = master_ssh(command)
    else:
        rc, _, _ = agent_ssh(agent_host, command)
    return rc == 0


def master_ssh(cmd: str, timeout_seconds=60, print_output=True, check=False) -> tuple:
    """
    Runs the provided command on the cluster leader, using ssh.
    Returns the exit code, stdout, and stderr as three separate values.
    """
    log.info("(SSH:leader) {}".format(cmd))
    return _ssh(cmd, _internal_leader_host(), timeout_seconds, print_output, check)


def agent_ssh(
    agent_host: str, cmd: str, timeout_seconds=60, print_output=True, check=False
) -> tuple:
    """
    Runs the provided command on the specified agent host, using ssh.
    Returns the exit code, stdout, and stderr as three separate values.
    """
    log.info("(SSH:agent={}) {}".format(agent_host, cmd))
    return _ssh(cmd, agent_host, timeout_seconds, print_output, check)


def master_scp(
    file_content: str, remote_path: str, timeout_seconds=60, print_output=True, check=False
) -> int:
    """
    Writes the provided input path to the specified path on cluster leader, using scp.
    Returns the exit code.
    """
    log.info("(SCP:leader) {} bytes => {}".format(len(file_content), remote_path))
    return _scp(
        file_content, remote_path, _internal_leader_host(), timeout_seconds, print_output, check
    )


def agent_scp(
    agent_host: str,
    file_content: str,
    remote_path: str,
    timeout_seconds=60,
    print_output=True,
    check=False,
) -> int:
    """
    Writes the provided input path to the specified path on the remote agent, using scp.
    Returns the exit code.
    """
    log.info("(SCP:agent={}) {} bytes => {}".format(agent_host, len(file_content), remote_path))
    return _scp(file_content, remote_path, agent_host, timeout_seconds, print_output, check)


def _ssh(cmd: str, host: str, timeout_seconds: int, print_output: bool, check: bool) -> tuple:
    common_args = " ".join(
        [
            # -oBatchMode=yes: Don't prompt for password if keyfile doesn't work.
            "-oBatchMode=yes",
            # -oStrictHostKeyChecking=no: Don't prompt for key signature on first connect.
            "-oStrictHostKeyChecking=no",
            # -oConnectTimeout=#: Limit the duration for the connection to be created.
            #                     We also configure a timeout for the command itself to run once connected, see below.
            "-oConnectTimeout={}".format(timeout_seconds),
            # -A: Forward the pubkey agent connection (required for nested access)
            "-A",
            # -q: Don't show banner, if any is configured, and suppress other warning/diagnostic messages.
            #     In particular, avoid messages that may mess up stdout/stderr output.
            "-q",
            # -l <user>: Username to log in as (depends on cluster OS, default to CoreOS)
            "-l {}".format(SSH_USERNAME),
        ]
    )

    if os.environ.get("DCOS_SSH_DIRECT", ""):
        # Direct SSH access to the node:
        ssh_cmd = 'ssh {} {} -- "{}"'.format(common_args, host, cmd)
    else:
        # Nested SSH call via the proxy node. Be careful to nest quotes to match, and escape any
        # command-internal double quotes as well:
        ssh_cmd = 'ssh {} {} -- "ssh {} {} -- \\"{}\\""'.format(
            common_args, _external_cluster_host(), common_args, host, cmd.replace('"', '\\\\\\"')
        )
    log.info("SSH command: {}".format(ssh_cmd))
    rc, stdout, stderr = _run_cmd(ssh_cmd, print_output, check, timeout_seconds=timeout_seconds)
    if rc == 255 and stdout == "":
        log.info("NOTE: This is likely due to misconfigured SSH credentials.")
        _run_cmd("ssh-add -L", print_output=True, check=False)
    return rc, stdout, stderr


def _scp(
    file_content: str,
    remote_path: str,
    host: str,
    timeout_seconds: int,
    print_output: bool,
    check: bool,
) -> int:
    common_args = " ".join(
        [
            # -oStrictHostKeyChecking=no: Don't prompt for key signature on first connect.
            "-oStrictHostKeyChecking=no",
            # -oConnectTimeout=#: Limit the duration for the connection to be created.
            #                     We also configure a timeout for the command itself to run once connected, see below.
            "-oConnectTimeout={}".format(timeout_seconds),
        ]
    )

    if os.environ.get("DCOS_SSH_DIRECT", ""):
        # Direct SSH access to the node:
        proxy_arg = ""
    else:
        # Nested SSH call via the proxy node. Be careful to nest quotes to match:
        # -A: Forward the pubkey agent connection (required for nested access)
        # -q: Don't show banner, if any is configured, and suppress other warning/diagnostic messages.
        #     In particular, avoid messages that may mess up stdout/stderr output.
        # -l <user>: Username to log in as (depends on cluster OS, default to CoreOS)
        proxy_arg = ' -oProxyCommand="ssh {} -A -q -l {} {}:22 {}"'.format(
            common_args, SSH_USERNAME, host, _external_cluster_host()
        )

    with tempfile.NamedTemporaryFile("w") as upload_file:
        upload_file.write(file_content)
        upload_file.flush()

        scp_cmd = "scp {}{} {} {}@{}:{}".format(
            common_args, proxy_arg, upload_file.name, SSH_USERNAME, host, remote_path
        )
        rc, _, _ = _run_cmd(scp_cmd, print_output, check, timeout_seconds=timeout_seconds)
        return rc


@functools.lru_cache()
def _external_cluster_host():
    """Returns the internet-facing IP of the cluster frontend."""
    return cluster_request("GET", "/metadata").json()["PUBLIC_IPV4"]


@functools.lru_cache()
def _internal_leader_host():
    """Returns the cluster-internal IP of the current mesos leader."""
    leader_hosts = cluster_request("GET", "/mesos_dns/v1/hosts/leader.mesos").json()
    if len(leader_hosts) == 0:
        # Just in case. Shouldn't happen in practice.
        raise Exception("Missing mesos-dns entry for leader.mesos: {}".format(leader_hosts))
    return leader_hosts[0]["ip"]


def marathon_task_exec(task_name: str, cmd: str) -> tuple:
    """
    Invokes the given command on the named Marathon task via `dcos task exec`.
    : param task_name: Name of task to run 'cmd' on.
    : param cmd: The command to execute.
    : return: a tuple consisting of the task exec's exit code, stdout, and stderr
    """
    # Marathon TaskIDs are of the form "<name>.<uuid>"
    return _task_exec(task_name, cmd)


def service_task_exec(service_name: str, task_name: str, cmd: str) -> tuple:
    """
    Invokes the given command on the named SDK service task via `dcos task exec`.
    : param service_name: Name of the service running the task.
    : param task_name: Name of task to run 'cmd' on.
    : param cmd: The command to execute.
    : return: a tuple consisting of the task exec's exit code, stdout, and stderr
    """

    # Contrary to CLI's help text for 'dcos task exec':
    # - 'partial task ID' is only prefix/startswith matching, not 'contains' as the wording would imply.
    # - Regexes don't work at all.
    # Therefore, we need to provide a full TaskID prefix, including "servicename__taskname":
    task_id_prefix = "{}__{}__".format(sdk_utils.get_task_id_service_name(service_name), task_name)
    rc, stdout, stderr = _task_exec(task_id_prefix, cmd)

    if "Cannot find a task with ID containing" in stderr:
        # If the service is doing an upgrade test, the old version may not use prefixed task ids.
        # Get around this by trying again without the service name prefix in the task id.
        rc, stdout, stderr = _task_exec(task_name, cmd)

    return rc, stdout, stderr


def _task_exec(task_id_prefix: str, cmd: str) -> tuple:
    if cmd.startswith("./") and sdk_utils.dcos_version_less_than("1.10"):
        # On 1.9 task exec is run relative to the host filesystem, not the container filesystem
        full_cmd = os.path.join(get_task_sandbox_path(task_id_prefix), cmd)

        if cmd.startswith("./bootstrap"):
            # On 1.9 we also need to set LIB_PROCESS_IP for bootstrap
            full_cmd = 'bash -c "LIBPROCESS_IP=0.0.0.0 {}"'.format(full_cmd)
    else:
        full_cmd = cmd

    return run_raw_cli("task exec {} {}".format(task_id_prefix, cmd))


def resolve_hosts(marathon_task_name: str, hosts: list, bootstrap_cmd: str = "./bootstrap") -> bool:
    """
    Use bootstrap to resolve the specified list of hosts
    """
    bootstrap_cmd_list = [
        bootstrap_cmd,
        "-print-env=false",
        "-template=false",
        "-install-certs=false",
        "-self-resolve=false",
        "-resolve-hosts",
        ",".join(hosts),
    ]
    log.info("Running bootstrap to wait for DNS resolution of: %s", ", ".join(hosts))
    _, bootstrap_stdout, bootstrap_stderr = marathon_task_exec(
        marathon_task_name, " ".join(bootstrap_cmd_list)
    )

    # Note that bootstrap returns its output in STDERR
    resolved = "SDK Bootstrap successful." in bootstrap_stderr
    if not resolved:
        for host in hosts:
            resolved_host_string = "Resolved '{host}' =>".format(host=host)
            host_resolved = resolved_host_string in bootstrap_stdout
            if not host_resolved:
                log.warning("Could not resolve: %s", host)

    return resolved


def get_json_output(cmd, print_output=True, check=False):
    _, stdout, stderr = run_raw_cli(cmd, print_output, check=check)

    if stderr:
        log.warning("stderr for command '%s' is non-empty: %s", cmd, stderr)

    try:
        json_stdout = jsonlib.loads(stdout)
    except Exception as e:
        log.warning("Error converting stdout to json:\n%s", stdout)
        raise e

    return json_stdout


def get_task_sandbox_path(task_id_prefix: str) -> str:
    task_info = _get_task_info(task_id_prefix)

    if not task_info:
        return ""

    executor_path = task_info["executor_id"]
    if not executor_path:
        executor_path = task_info["id"]

    # Assume the latest run:
    return os.path.join(
        "/var/lib/mesos/slave/slaves",
        task_info["slave_id"],
        "frameworks",
        task_info["framework_id"],
        "executors",
        executor_path,
        "runs/latest",
    )


@retrying.retry(stop_max_attempt_number=3, wait_fixed=2000)
def _get_task_info(task_id_prefix: str) -> dict:
    """
    : return (dict): Get the task information for the specified task
    """
    raw_tasks = run_cli("task {task_id_prefix} --json".format(task_id_prefix=task_id_prefix))
    if not raw_tasks:
        log.warning("No data returned for tasks matching id '%s'", task_id_prefix)
        return {}

    tasks = jsonlib.loads(raw_tasks)
    for task in tasks:
        if task.get("id", "").startswith(task_id_prefix):
            log.info("Matched on 'id': ")
            return task
    log.warning(
        "Didn't find task matching id '%s'. Found: %s",
        task_id_prefix,
        ",".join([t.get("id", "NO-ID") for t in tasks]),
    )
    return {}
