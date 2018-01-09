'''Utilities relating to running commands and HTTP requests

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_cmd IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import json as jsonlib
import logging
import retrying
import subprocess
import traceback

import dcos.http
import shakedown

log = logging.getLogger(__name__)

DEFAULT_TIMEOUT_SECONDS = 30 * 60


def request(method, url, retry=True, log_args=True, verify=None, **kwargs):
    def fn():
        response = dcos.http.request(method, url, verify=verify, **kwargs)
        if log_args:
            log.info('Got {} for {} {} (args: {})'.format(
                response.status_code, method.upper(), url, kwargs))
        else:
            log.info('Got {} for {} {} ({} args)'.format(
                response.status_code, method.upper(), url, len(kwargs)))
        response.raise_for_status()
        return response
    if retry:
        @retrying.retry(
            wait_fixed=1000,
            stop_max_delay=60*1000,
            retry_on_result=lambda res: not res)
        def retry_fn():
            try:
                return fn()
            except:
                log.info(traceback.format_exc())
                return None
        return retry_fn()
    else:
        return fn()


def svc_cli(package_name, service_name, service_cmd, json=False, print_output=True, return_stderr_in_stdout=False):
    full_cmd = '{} --name={} {}'.format(package_name, service_name, service_cmd)

    if not json:
        return run_cli(full_cmd, print_output=print_output, return_stderr_in_stdout=return_stderr_in_stdout)
    else:
        # TODO(elezar): We shouldn't use json=True and return_stderr_in_stdout=True together
        # assert not return_stderr_in_stdout, json=True and return_stderr_in_stdout=True together should not be used together
        return get_json_output(full_cmd, print_output=print_output)


def run_raw_cli(cmd, print_output=True):
    """Runs the command with `dcos` as the prefix to the shell command
    and returns the resulting output (stdout seperated from stderr by a newline).

    eg. `cmd`= "package install <package-name>" results in:
    $ dcos package install <package-name>
    """
    dcos_cmd = "dcos {}".format(cmd)
    result = subprocess.run([dcos_cmd], shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    stdout = ""
    stderr = ""

    if result.stdout:
        stdout = result.stdout.decode('utf-8').strip()

    if result.stderr:
        stderr = result.stderr.decode('utf-8').strip()

    if print_output:
        if stdout:
            log.info("STDOUT:\n{}".format(stdout))
        if stderr:
            log.info("STDERR:\n{}".format(stderr))

    return result.returncode, stdout, stderr


def run_cli(cmd, print_output=True, return_stderr_in_stdout=False):

    _, stdout, stderr = run_raw_cli(cmd, print_output)

    if return_stderr_in_stdout:
        return stdout + "\n" + stderr

    return stdout


def kill_task_with_pattern(pattern, agent_host=None, timeout_seconds=DEFAULT_TIMEOUT_SECONDS):
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: not res)
    def fn():
        command = (
            "sudo kill -9 "
            "$(ps ax | grep {} | grep -v grep | tr -s ' ' | sed 's/^ *//g' | "
            "cut -d ' ' -f 1)".format(pattern))
        if agent_host is None:
            exit_status, _ = shakedown.run_command_on_master(command)
        else:
            exit_status, _ = shakedown.run_command_on_agent(agent_host, command)

        return exit_status

    # might not be able to connect to the agent on first try so we repeat until we can
    fn()


def shutdown_agent(agent_ip, timeout_seconds=DEFAULT_TIMEOUT_SECONDS):
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=timeout_seconds*1000,
        retry_on_result=lambda res: not res)
    def fn():
        status, stdout = shakedown.run_command_on_agent(agent_ip, 'sudo shutdown -h +1')
        log.info('Shutdown agent {}: [{}] {}'.format(agent_ip, status, stdout))
        return status
    # might not be able to connect to the agent on first try so we repeat until we can
    fn()

    log.info('Waiting for agent {} to appear unresponsive'.format(agent_ip))
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=300*1000, # 5 minutes
        retry_on_result=lambda res: res)
    def wait_for_unresponsive_agent():
        status, stdout = shakedown.run_command_on_agent(agent_ip, 'ls')
        log.info('ls stdout: {}'.format(stdout))
        return status

    wait_for_unresponsive_agent()


def task_exec(task_name: str, cmd: str, return_stderr_in_stdout: bool = False) -> tuple:
    """
    Invokes the given command on the task via `dcos task exec`.
    :param task_name: Name of task to run command on.
    :param cmd: The command to execute.
    :return: a tuple consisting of the task exec's return code, stdout, and stderr
    """
    exec_cmd = "task exec {task_name} {cmd}".format(task_name=task_name, cmd=cmd)
    rc, stdout, stderr = run_raw_cli(exec_cmd)

    if return_stderr_in_stdout:
        return rc, stdout + "\n" + stderr

    return rc, stdout, stderr


def get_json_output(cmd, print_output=True):
    _, stdout, stderr = run_raw_cli(cmd, print_output)

    if stderr:
        log.warning("stderr for command '%s' is non-empty: %s", cmd, stderr)

    try:
        json_stdout = jsonlib.loads(stdout)
    except Exception as e:
        log.warning("Error converting stdout to json:\n%s", stdout)
        raise e

    return json_stdout
