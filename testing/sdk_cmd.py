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


def request(method, url, retry=True, log_args=True, **kwargs):
    def fn():
        response = dcos.http.request(method, url, **kwargs)
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
    result = run_cli(full_cmd, print_output=print_output, return_stderr_in_stdout=return_stderr_in_stdout)
    if json:
        return jsonlib.loads(result)
    return result


def run_cli(cmd, print_output=True, return_stderr_in_stdout=False):
    (stdout, stderr, ret) = shakedown.run_dcos_command(
        cmd, print_output=print_output)
    if ret != 0:
        err = 'Got error code {} when running command "dcos {}":\nstdout: "{}"\nstderr: "{}"'.format(
            ret, cmd, stdout, stderr)
        log.error(err)
        raise dcos.errors.DCOSException(err)
    if return_stderr_in_stdout:
        stdout = stdout + "\n" + stderr
    return stdout
