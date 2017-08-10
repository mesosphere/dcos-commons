'''Utilities relating to running commands and HTTP requests'''
import logging
import ast

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


def run_cli(cmd, print_output=True, return_stderr_in_stdout=False):
    (stdout, stderr, ret) = shakedown.run_dcos_command(cmd, print_output=print_output)
    if ret != 0:
        err = 'Got error code {} when running command "dcos {}":\nstdout: "{}"\nstderr: "{}"'.format(
            ret, cmd, stdout, stderr)
        log.error(err)
        raise dcos.errors.DCOSException(err)
    if return_stderr_in_stdout:
        stdout = stdout + "\n" + stderr
    return stdout


def convert_output_to_list(output):
    """When a list is the expected output of a command,
    this converts the string output to said expected list.

    In spite of being a one-liner it standardizes the conversion.
    """
    return [element.strip() for element in ast.literal_eval(output)]
