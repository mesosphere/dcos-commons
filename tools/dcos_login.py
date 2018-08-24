#!/usr/bin/env python3
import json
import logging
import os
import ssl
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
            log_args=False,
            data=json.dumps(payload).encode("utf-8"),
        )
    )["token"]


def _netloc(url: str):
    return url.split("-1")[-1]


def attach_cluster(cluster_id: str) -> None:
    """Adds an 'attached' file to the desired cluster_id, and removes any attached files from any
    other clusters.
    """
    if not os.path.isdir(__CLUSTERS_PATH):
        raise Exception("INTERNAL ERROR: Missing clusters directory: {}".format(__CLUSTERS_PATH))
    for name in os.listdir(__CLUSTERS_PATH):
        cluster_path = os.path.join(__CLUSTERS_PATH, name)
        if not os.path.isdir(cluster_path):
            continue
        attached_file_path = os.path.join(cluster_path, "attached")
        if name == cluster_id:
            if not os.path.isfile(attached_file_path):
                log.info("Attaching cluster: {}".format(cluster_id))
                f = open(attached_file_path, "w")
                f.close()
                os.chmod(attached_file_path, 0o600)
        elif os.path.isfile(attached_file_path):
            log.info("Detaching cluster: {}".format(name))
            os.unlink(attached_file_path)


def configure_cli(dcosurl: str, token: str) -> None:
    """Sets up a dcos cluster config for the specified cluster using the specified auth token."""
    cluster_id = json.loads(http_request("GET", dcosurl, "/metadata", token))["CLUSTER_ID"]
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

    # Write the cluster config file:
    cluster_dir_path = os.path.join(__CLUSTERS_PATH, cluster_id)
    os.makedirs(cluster_dir_path, exist_ok=True)
    cluster_config_path = os.path.join(cluster_dir_path, "dcos.toml")
    with open(cluster_config_path, "w") as f:
        f.write(__TOML_TEMPLATE.format(name=state_summary["cluster"], token=token, url=dcosurl))
    os.chmod(cluster_config_path, 0o600)

    # Write the 'attach' file:
    attach_cluster(cluster_id)


def login_session() -> None:
    """Login to DC/OS.

    Behavior is determined by the following environment variables:
    CLUSTER_URL: full URL to the test cluster
    DCOS_LOGIN_USERNAME: the EE user (defaults to bootstrapuser)
    DCOS_LOGIN_PASSWORD: the EE password (defaults to deleteme)
    DCOS_ENTERPRISE: determine how to authenticate (defaults to false)
    DCOS_ACS_TOKEN: bypass auth and use the user supplied token
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
    dcos_enterprise = ignore_empty("DCOS_ENTERPRISE", "true").lower() == "true"
    dcos_acs_token = os.environ.get("DCOS_ACS_TOKEN")
    if not dcos_acs_token:
        dcos_acs_token = login(
            dcosurl=cluster_url,
            username=dcos_login_username,
            password=dcos_login_password,
            is_enterprise=dcos_enterprise,
        )
    configure_cli(dcosurl=cluster_url, token=dcos_acs_token)


if __name__ == "__main__":
    login_session()
