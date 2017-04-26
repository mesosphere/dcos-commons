'''Utilities relating to running commands and HTTP requests'''

import dcos.http
import sdk_spin
import sdk_utils
import shakedown


def request(method, url, retry=True, **kwargs):
    def fn():
        response = dcos.http.request(method, url, **kwargs)
        sdk_utils.out('Got {} for {} {} (args: {})'.format(
            response.status_code, method.upper(), url, kwargs))
        response.raise_for_status()
        return response
    if retry:
        return shakedown.wait_while_exceptions(lambda: fn())
    else:
        return fn()


def run_cli(cmd):
    (stdout, stderr, ret) = shakedown.run_dcos_command(cmd)
    if ret != 0:
        err = 'Got error code {} when running command "dcos {}":\nstdout: "{}"\nstderr: "{}"'.format(
            ret, cmd, stdout, stderr)
        sdk_utils.out(err)
        raise Exception(err)
    return stdout
