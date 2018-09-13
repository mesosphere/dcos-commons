#!/usr/bin/env python3
import json
import logging
import os
import ssl
import subprocess
import time
import urllib.parse
import urllib.request

log = logging.getLogger(__name__)

__CLI_LOGIN_OPEN_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik9UQkVOakZFTWtWQ09VRTRPRVpGTlRNMFJrWXlRa015Tnprd1JrSkVRemRCTWpBM1FqYzVOZyJ9.eyJlbWFpbCI6ImFsYmVydEBiZWtzdGlsLm5ldCIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJpc3MiOiJodHRwczovL2Rjb3MuYXV0aDAuY29tLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTA5OTY0NDk5MDExMTA4OTA1MDUwIiwiYXVkIjoiM3lGNVRPU3pkbEk0NVExeHNweHplb0dCZTlmTnhtOW0iLCJleHAiOjIwOTA4ODQ5NzQsImlhdCI6MTQ2MDE2NDk3NH0.OxcoJJp06L1z2_41_p65FriEGkPzwFB_0pA9ULCvwvzJ8pJXw9hLbmsx-23aY2f-ydwJ7LSibL9i5NbQSR2riJWTcW4N7tLLCCMeFXKEK4hErN2hyxz71Fl765EjQSO5KD1A-HsOPr3ZZPoGTBjE0-EFtmXkSlHb1T2zd0Z8T5Z2-q96WkFoT6PiEdbrDA-e47LKtRmqsddnPZnp0xmMQdTr2MjpVgvqG7TlRvxDcYc-62rkwQXDNSWsW61FcKfQ-TRIZSf2GS9F9esDF4b5tRtrXcBNaorYa9ql0XAWH5W_ct4ylRNl3vwkYKWa4cmPvOqT5Wlj9Tf0af4lNO40PQ"  # noqa
__CLI_LOGIN_EE_USERNAME = "bootstrapuser"
__CLI_LOGIN_EE_PASSWORD = "deleteme"

__REQUEST_ATTEMPTS = 5
__REQUEST_ATTEMPT_SLEEP_SECONDS = 2

__CLUSTERS_PATH = os.path.expanduser(os.path.join("~", ".dcos", "clusters"))
__TOML_TEMPLATE = """[cluster]
name = "{name}"

[core]
dcos_acs_token = "{token}"
dcos_url = "{url}"
ssl_verify = "false"
"""

logging.basicConfig(format="[%(asctime)s|%(levelname)s]: %(message)s", level="INFO")
log = logging.getLogger(__name__)


def http_request(
    method: str,
    cluster_url: str,
    cluster_path: str,
    token: str,
    headers={},
    log_args=True,
    data=None,
):
    """Performs an http request, returning the text content on success, or throwing an exception on
    consistent failure.

    To simplify portability, this internally sticks to only using python3 stdlib.
    """

    query_url = urllib.parse.urljoin(cluster_url, cluster_path)
    if token:
        headers["Authorization"] = "token={}".format(token)
    request = urllib.request.Request(query_url, method=method, headers=headers, unverifiable=True)

    # Disable SSL cert: test clusters are usually self-signed
    ignore_ssl_cert = ssl.create_default_context()
    ignore_ssl_cert.check_hostname = False
    ignore_ssl_cert.verify_mode = ssl.CERT_NONE

    for i in range(__REQUEST_ATTEMPTS):
        start = time.time()
        try:
            response = urllib.request.urlopen(
                request, data=data, timeout=10, context=ignore_ssl_cert
            )
        except Exception:
            log.error("Query failed: {} {}".format(method, query_url))
            raise
        end = time.time()

        response_status = response.getcode()
        log_msg = "(HTTP {}) {} => {} ({:.3f}s)".format(
            method.upper(), cluster_path, response_status, end - start
        )
        encoding = response.info().get_content_charset("utf-8")
        response_data = response.read().decode(encoding)
        if response_status == 200:
            log.info(log_msg)
            return response_data
        else:
            log.error("{}\n{}".format(log_msg, response_data))
            time.sleep(__REQUEST_ATTEMPT_SLEEP_SECONDS)

    raise Exception(
        "Failed to complete {} {} request after {} attempts".format(
            method, query_url, __REQUEST_ATTEMPTS
        )
    )


def _netloc(url: str):
    return url.split("-1")[-1]


def login_session() -> None:
    """Login to DC/OS.

    Behavior is determined by the following environment variables:
    CLUSTER_URL: full URL to the test cluster
    DCOS_LOGIN_USERNAME: the EE user (defaults to bootstrapuser)
    DCOS_LOGIN_PASSWORD: the EE password (defaults to deleteme)
    """
    cluster_url = os.environ.get("CLUSTER_URL")
    if not cluster_url:
        raise Exception("Must have CLUSTER_URL set in environment!")

    def ignore_empty(envvar, default):
        # Ignore the user passing in empty ENVVARs.
        value = os.environ.get(envvar, "").strip()
        if not value:
            return default

        return value

    dcos_login_username = ignore_empty("DCOS_LOGIN_USERNAME", __CLI_LOGIN_EE_USERNAME)
    dcos_login_password = ignore_empty("DCOS_LOGIN_PASSWORD", __CLI_LOGIN_EE_PASSWORD)
    subprocess.run(["dcos", "cluster", "setup", cluster_url, "--username", dcos_login_username, "--password", dcos_login_password])


if __name__ == "__main__":
    login_session()
