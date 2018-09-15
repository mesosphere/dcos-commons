#!/usr/bin/env python3

import json
import logging
import os
import ssl
import subprocess
import time
import urllib.parse
import urllib.request
from urllib.error import URLError


__CLI_LOGIN_OPEN_TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik9UQkVOakZFTWtWQ09VRTRPRVpGTlRNMFJrWXlRa015Tnprd1JrSkVRemRCTWpBM1FqYzVOZyJ9.eyJlbWFpbCI6ImFsYmVydEBiZWtzdGlsLm5ldCIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJpc3MiOiJodHRwczovL2Rjb3MuYXV0aDAuY29tLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTA5OTY0NDk5MDExMTA4OTA1MDUwIiwiYXVkIjoiM3lGNVRPU3pkbEk0NVExeHNweHplb0dCZTlmTnhtOW0iLCJleHAiOjIwOTA4ODQ5NzQsImlhdCI6MTQ2MDE2NDk3NH0.OxcoJJp06L1z2_41_p65FriEGkPzwFB_0pA9ULCvwvzJ8pJXw9hLbmsx-23aY2f-ydwJ7LSibL9i5NbQSR2riJWTcW4N7tLLCCMeFXKEK4hErN2hyxz71Fl765EjQSO5KD1A-HsOPr3ZZPoGTBjE0-EFtmXkSlHb1T2zd0Z8T5Z2-q96WkFoT6PiEdbrDA-e47LKtRmqsddnPZnp0xmMQdTr2MjpVgvqG7TlRvxDcYc-62rkwQXDNSWsW61FcKfQ-TRIZSf2GS9F9esDF4b5tRtrXcBNaorYa9ql0XAWH5W_ct4ylRNl3vwkYKWa4cmPvOqT5Wlj9Tf0af4lNO40PQ"  # noqa
__CLI_LOGIN_EE_USERNAME = "bootstrapuser"
__CLI_LOGIN_EE_PASSWORD = "deleteme"

__CLUSTER_URL_ENV = "CLUSTER_URL"

__REQUEST_ATTEMPTS = 5
__REQUEST_ATTEMPT_SLEEP_SECONDS = 2

logging.basicConfig(format="[%(asctime)s|%(levelname)s]: %(message)s", level="INFO")
log = logging.getLogger(__name__)


def http_request(
    method: str,
    cluster_url: str,
    cluster_path: str,
    token=None,
    headers={},
    data=None,
) -> str:
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
        except URLError as err:
            log.error(err.reason)
            raise
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


def login(dcosurl: str, username: str, password: str, is_enterprise: bool) -> str:
    """Logs into the cluster, or throws an exception if login fails.

    This internally implements a retry loop. We could use 'retrying', but this utility sticks to
    the python stdlib to allow easy portability.
    """
    if is_enterprise:
        log.info("Logging into {} as {}".format(dcosurl, username))
        payload = {"uid": username, "password": password}
    else:
        log.info("Logging into {} with default open token".format(dcosurl))
        payload = {"token": __CLI_LOGIN_OPEN_TOKEN}

    return json.loads(
        http_request(
            "POST",
            dcosurl,
            "/acs/api/v1/auth/login",
            token=None,
            headers={"Content-Type": "application/json"},
            data=json.dumps(payload).encode("utf-8"),
        )
    )["token"]


def print_state_summary(dcosurl: str, token: str) -> None:
    """Sets up a dcos cluster config for the specified cluster using the specified auth token."""
    state_summary = json.loads(http_request("GET", dcosurl, "/mesos/state-summary", token))

    # Since we've got the state summary, print out some cluster stats:
    agents = []
    public_count = 0
    for agent in state_summary.get("slaves", []):
        # TODO log mount volumes. how do they look?
        is_public = "public_ip" in agent.get("attributes", {})
        if is_public:
            public_count += 1
        agents.append(
            "- {} ({}): {} cpu, {} mem, {} disk".format(
                agent.get("hostname", "???"),
                "public" if is_public else "private",
                agent.get("resources", {}).get("cpus", 0),
                agent.get("resources", {}).get("mem", 0),
                agent.get("resources", {}).get("disk", 0),
            )
        )
    log.info(
        "Configured cluster with {} public/{} private agents:\n{}".format(
            public_count, len(agents) - public_count, "\n".join(agents)
        )
    )


def login_session(cluster_url: str, is_old_cli: bool) -> None:
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
         |- If Open:
            |- Use `dcos config set` for all the below properties
               |- core.dcos_url
               |- core.ssl_verify
               |- core.dcos_acs_token
         |- If Enterprise:
            |- A `dcos auth login` with username and password should work.
    """
    def ignore_empty(envvar, default):
        # Ignore the user passing in empty ENVVARs.
        value = os.environ.get(envvar, "").strip()
        if not value:
            return default

        return value

    dcos_login_username = ignore_empty("DCOS_LOGIN_USERNAME", __CLI_LOGIN_EE_USERNAME)
    dcos_login_password = ignore_empty("DCOS_LOGIN_PASSWORD", __CLI_LOGIN_EE_PASSWORD)
    dcos_enterprise = ignore_empty("DCOS_ENTERPRISE", "true").lower() == "true"
    dcos_acs_token = os.environ.get("DCOS_ACS_TOKEN", login(
        dcosurl=cluster_url,
        username=dcos_login_username,
        password=dcos_login_password,
        is_enterprise=dcos_enterprise,
    ))
    if is_old_cli:
        # TODO(takirala) : We can remove this code path few months after 1.12 GA
        if dcos_enterprise:
            _run_cmd("dcos auth login {} --username {} --password {}".format(
                cluster_url,
                dcos_login_username,
                dcos_login_password))
        else:
            _run_cmd("dcos config set core.dcos_url {}".format(cluster_url))
            _run_cmd("dcos config set core.ssl_verify {}".format(False))
            _run_cmd("dcos config set core.dcos_acs_token {}".format(dcos_acs_token))
    else:
        if dcos_enterprise:
            _run_cmd("dcos cluster setup {} --username {} --password {} --insecure".format(
                cluster_url,
                dcos_login_username,
                dcos_login_password))
        else:
            dcos_acs_token = os.environ.get("DCOS_ACS_TOKEN", dcos_acs_token)
            _run_cmd("env DCOS_ACS_TOKEN={} dcos cluster setup {} --insecure".format(
                dcos_acs_token, cluster_url))

    print_state_summary(dcosurl=cluster_url, token=dcos_acs_token)


def check_and_install_native_cli(cluster_url: str) -> bool:
    old_cli = "https://downloads.dcos.io/binaries/cli/linux/x86-64/dcos-1.9/dcos"
    response = http_request("GET", cluster_url, "dcos-metadata/dcos-version.json")
    log.info("Version response for cluster {} is {}".format(cluster_url, response))
    version = json.loads(response)["version"]
    if version.startswith("1.9"):
        rc, old_path, _ = _run_cmd("which dcos")
        assert rc == 0, "dcos command not found"
        rc, _, _ = _run_cmd("wget {} -o {}".format(old_cli, old_path))
        assert rc == 0, "Installation of old cli failed"
        return True
    return False


def _run_cmd(cmd):
    result = subprocess.run(
        [cmd],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        shell=True,
        check=True,
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
    login_session(_cluster_url, check_and_install_native_cli(_cluster_url))
