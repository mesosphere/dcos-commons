'''Utilities relating to running commands and HTTP requests'''

import dcos.http
import sdk_utils
import shakedown


def request(method, url, retry=True, log_args=True, **kwargs):
    def fn():
        response = dcos.http.request(method, url, **kwargs)
        if log_args:
            sdk_utils.out('Got {} for {} {} (args: {})'.format(
                response.status_code, method.upper(), url, kwargs))
        else:
            sdk_utils.out('Got {} for {} {} ({} args)'.format(
                response.status_code, method.upper(), url, len(kwargs)))
        response.raise_for_status()
        return response
    if retry:
        return shakedown.wait_while_exceptions(lambda: fn())
    else:
        return fn()


def run_cli(cmd, print_output=True):
    (stdout, stderr, ret) = shakedown.run_dcos_command(cmd, print_output=print_output)
    if ret != 0:
        err = 'Got error code {} when running command "dcos {}":\nstdout: "{}"\nstderr: "{}"'.format(
            ret, cmd, stdout, stderr)
        sdk_utils.out(err)
        raise dcos.errors.DCOSException(err)
    return stdout
