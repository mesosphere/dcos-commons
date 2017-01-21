#!/usr/bin/python

import shakedown

# Utilities relating to running commands and HTTP requests


def request(request_fn, *args, **kwargs):
    def success_predicate(response):
        return (response.status_code == 200, 'Request failed: %s' % response.content)

    return spin(request_fn, success_predicate, *args, **kwargs)


def run_dcos_cli_cmd(cmd):
    (stdout, stderr, ret) = shakedown.run_dcos_command(cmd)
    if ret != 0:
        err = "Got error code {} when running command 'dcos {}':\nstdout: {}\nstderr: {}".format(
            ret, cmd, stdout, stderr)
        print(err)
        raise Exception(err)
    return stdout
