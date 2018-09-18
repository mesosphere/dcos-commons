#!/usr/bin/env python3

import logging
import os
import subprocess

__CLI_LOGIN_OPEN_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik9UQkVOakZFTWtWQ09VRTRPRVpGTlRNMFJrWXlRa015Tnprd1JrSkVRemRCTWpBM1FqYzVOZyJ9.eyJlbWFpbCI6ImFsYmVydEBiZWtzdGlsLm5ldCIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJpc3MiOiJodHRwczovL2Rjb3MuYXV0aDAuY29tLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTA5OTY0NDk5MDExMTA4OTA1MDUwIiwiYXVkIjoiM3lGNVRPU3pkbEk0NVExeHNweHplb0dCZTlmTnhtOW0iLCJleHAiOjIwOTA4ODQ5NzQsImlhdCI6MTQ2MDE2NDk3NH0.OxcoJJp06L1z2_41_p65FriEGkPzwFB_0pA9ULCvwvzJ8pJXw9hLbmsx-23aY2f-ydwJ7LSibL9i5NbQSR2riJWTcW4N7tLLCCMeFXKEK4hErN2hyxz71Fl765EjQSO5KD1A-HsOPr3ZZPoGTBjE0-EFtmXkSlHb1T2zd0Z8T5Z2-q96WkFoT6PiEdbrDA-e47LKtRmqsddnPZnp0xmMQdTr2MjpVgvqG7TlRvxDcYc-62rkwQXDNSWsW61FcKfQ-TRIZSf2GS9F9esDF4b5tRtrXcBNaorYa9ql0XAWH5W_ct4ylRNl3vwkYKWa4cmPvOqT5Wlj9Tf0af4lNO40PQ"  # noqa
__CLI_LOGIN_EE_USERNAME = "bootstrapuser"
__CLI_LOGIN_EE_PASSWORD = "deleteme"

__CLUSTER_URL_ENV = "CLUSTER_URL"
__DCOS_ACS_TOKEN_ENV = "DCOS_ACS_TOKEN"

__REQUEST_ATTEMPTS = 5
__REQUEST_ATTEMPT_SLEEP_SECONDS = 2

logging.basicConfig(format="[%(asctime)s|%(levelname)s]: %(message)s", level="INFO")
log = logging.getLogger(__name__)


def login_session(cluster_url: str) -> None:
    """Login to DC/OS.

    Behavior is determined by the following environment variables:
    CLUSTER_URL: full URL to the test cluster
    DCOS_LOGIN_USERNAME: the EE user (defaults to bootstrapuser)
    DCOS_LOGIN_PASSWORD: the EE password (defaults to deleteme)
    DCOS_ENTERPRISE: determine how to authenticate (defaults to false)
    DCOS_ACS_TOKEN: bypass auth and use the user supplied token

    This is the current flow to login to a cluster
    - Dockerfile downloads the latest cli that works for 1.10 and above ONLY.
      |- If the cluster is 1.10 and above:
         |- If Open:
            |- Export DCOS_ACS_TOKEN
            |- Then do a `dcos cluster setup <url>`, it should magically work!
         |- If Enterprise:
            |- A `dcos cluster setup` with username and password should work.
      |- If the cluster is less than 1.10: <WE ONLY SUPPORT 1.9 - TO BE DEPRECATED>
         |- Overwrite the binary to 1.9
         |- For Open AND Enterprise, use `dcos config set` for all the below properties:
            |- core.dcos_url
            |- core.ssl_verify
            |- core.dcos_acs_token
    """
    def ignore_empty(envvar, default):
        # Ignore the user passing in empty ENVVARs.
        value = os.environ.get(envvar, "").strip()
        return value if value else default

    dcos_login_username = ignore_empty("DCOS_LOGIN_USERNAME", __CLI_LOGIN_EE_USERNAME)
    dcos_login_password = ignore_empty("DCOS_LOGIN_PASSWORD", __CLI_LOGIN_EE_PASSWORD)
    if ignore_empty("DCOS_ENTERPRISE", "true").lower() == "true":
        _run_cmd(
            "dcos cluster setup {} --username {} --password {} --insecure".format(
                cluster_url,
                dcos_login_username,
                dcos_login_password
            ),
            check=True)
    else:
        # This would try to use `xdg-open` and print a warning that it was not found in the PATH,
        # but reads stdin and cluster will setup successfully.
        rc, _, _ = _run_cmd(
            "dcos cluster setup {} --insecure".format(cluster_url),
            check=True,
            cmd_input=bytes(os.environ.get(__DCOS_ACS_TOKEN_ENV, __CLI_LOGIN_OPEN_TOKEN), encoding="utf-8"))
    _run_cmd("dcos node --json", check=True)


def _run_cmd(cmd, check=False, cmd_input=None):
    log.info("[CMD] {}".format(cmd))
    result = subprocess.run(
        [cmd],
        input=cmd_input,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        shell=True,
        check=check,
    )

    if result.returncode != 0:
        log.info("Got exit code {} to command: {}".format(result.returncode, cmd))

    if result.stdout:
        stdout = result.stdout.decode("utf-8").strip()
        log.info("STDOUT:\n{}".format(stdout))
    else:
        stdout = ""

    if result.stderr:
        stderr = result.stderr.decode("utf-8").strip()
        log.info("STDERR:\n{}".format(stderr))
    else:
        stderr = ""

    return result.returncode, stdout, stderr


if __name__ == "__main__":
    if __CLUSTER_URL_ENV not in os.environ:
        raise Exception("Must have {} set in environment!".format(__CLUSTER_URL_ENV))
    _cluster_url = os.environ.get(__CLUSTER_URL_ENV)
    login_session(_cluster_url)
