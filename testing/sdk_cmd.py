'''Utilities relating to running commands and HTTP requests'''

import dcos.http
import shakedown


def request(method, url, retry=True, **kwargs):
    def fn():
        response = dcos.http.request(method, url, **kwargs)
        print('Got {} for {} {} (args: {})'.format(
            response.status_code, method.upper(), url, kwargs))
        response.raise_for_status()
        return response
    if retry:
        return shakedown.wait_for(lambda: fn())
    else:
        return fn()


def run_cli(cmd):
    (stdout, stderr, ret) = shakedown.run_dcos_command(cmd, raise_on_error=True)
    return stdout
