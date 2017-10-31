'''Utilities relating to running commands and HTTP requests

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_cmd IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import json as jsonlib
import logging

import dcos.http
import shakedown

log = logging.getLogger(__name__)


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
        return shakedown.wait_while_exceptions(lambda: fn())
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


def run_raw_cli(cmd, print_output):
    """Runs the command with `dcos` as the prefix to the shell command
    and returns the resulting output (stdout seperated from stderr by a newline).

    eg. `cmd`= "package install <package-name>" results in:
    $ dcos package install <package-name>
    """
    stdout, stderr, ret = shakedown.run_dcos_command(cmd, print_output=print_output)
    if ret:
        err = 'Got error code {} when running command "dcos {}":\n'\
              'stdout: "{}"\n'\
              'stderr: "{}"'.format(ret, cmd, stdout, stderr)
        log.error(err)
        raise dcos.errors.DCOSException(err)

    return stdout, stderr


def run_cli(cmd, print_output=True, return_stderr_in_stdout=False):

    stdout, stderr = run_raw_cli(cmd, print_output)

    if return_stderr_in_stdout:
        return stdout + "\n" + stderr
    else:
        return stdout


def get_json_output(cmd, print_output=True):
    stdout, stderr = run_raw_cli(cmd, print_output)

    if stderr:
        log.warn("stderr for command '%s' is non-empty: %s", cmd, stderr)

    try:
        json_stdout = jsonlib.loads(stdout)
    except Exception as e:
        log.error("Error converting stdout=%s to json", stdout)
        log.error(e)
        raise e

    return json_stdout
