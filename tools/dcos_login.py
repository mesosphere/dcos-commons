#!/usr/bin/env python3
import logging
import os

import dcos.cluster
import requests

from dcos_test_utils import logger

__CLI_LOGIN_OPEN_TOKEN = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik9UQkVOakZFTWtWQ09VRTRPRVpGTlRNMFJrWXlRa015Tnprd1JrSkVRemRCTWpBM1FqYzVOZyJ9.eyJlbWFpbCI6ImFsYmVydEBiZWtzdGlsLm5ldCIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJpc3MiOiJodHRwczovL2Rjb3MuYXV0aDAuY29tLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTA5OTY0NDk5MDExMTA4OTA1MDUwIiwiYXVkIjoiM3lGNVRPU3pkbEk0NVExeHNweHplb0dCZTlmTnhtOW0iLCJleHAiOjIwOTA4ODQ5NzQsImlhdCI6MTQ2MDE2NDk3NH0.OxcoJJp06L1z2_41_p65FriEGkPzwFB_0pA9ULCvwvzJ8pJXw9hLbmsx-23aY2f-ydwJ7LSibL9i5NbQSR2riJWTcW4N7tLLCCMeFXKEK4hErN2hyxz71Fl765EjQSO5KD1A-HsOPr3ZZPoGTBjE0-EFtmXkSlHb1T2zd0Z8T5Z2-q96WkFoT6PiEdbrDA-e47LKtRmqsddnPZnp0xmMQdTr2MjpVgvqG7TlRvxDcYc-62rkwQXDNSWsW61FcKfQ-TRIZSf2GS9F9esDF4b5tRtrXcBNaorYa9ql0XAWH5W_ct4ylRNl3vwkYKWa4cmPvOqT5Wlj9Tf0af4lNO40PQ'  # noqa
__CLI_LOGIN_EE_USERNAME = 'bootstrapuser'
__CLI_LOGIN_EE_PASSWORD = 'deleteme'

log = logging.getLogger(__name__)


def login(dcosurl: str, username: str, password: str, is_enterprise: bool) -> str:
    if is_enterprise:
        log.info('logging into {} as {}'.format(dcosurl, username))
        payload = {'uid': username, 'password': password}
    else:
        log.info('logging into {} with default open token'.format(dcosurl))
        payload = {'token': __CLI_LOGIN_OPEN_TOKEN}

    headers = {'Content-Type': 'application/json'}
    login_endpoint = '{dcosurl}/acs/api/v1/auth/login'.format(dcosurl=dcosurl)
    r = requests.post(login_endpoint, headers=headers, json=payload, verify=False)
    assert r.status_code == 200, '{} failed {}: {}'.format(login_endpoint, r.status_code, r.text)

    return r.json()['token']


def _netloc(url: str):
    return url.split('-1')[-1]


def configure_cli(dcosurl: str, token: str) -> None:
    for cluster in dcos.cluster.get_clusters():
        # check to see if the target cluster has been configured
        if _netloc(cluster.get_url()) == _netloc(dcosurl):
            dcos.cluster.set_attached(cluster.cluster_path)
            # cluster attached successfully, can begin using CLI/tests
            return
    with dcos.cluster.setup_directory() as temp_path:
        dcos.cluster.set_attached(temp_path)
        dcos.config.set_val('core.dcos_url', dcosurl)
        dcos.config.set_val('core.ssl_verify', 'False')
        dcos.config.set_val('core.dcos_acs_token', token)
        dcos.cluster.setup_cluster_config(dcosurl, temp_path, False)


def logout(dcosurl: str):
    pass


def login_session() -> None:
    """Login to DC/OS.

    Behavior is determined by the following environment variables:
    CLUSTER_URL: full URL to the test cluster
    DCOS_LOGIN_USERNAME: the EE user (defaults to bootstrapuser)
    DCOS_LOGIN_PASSWORD: the EE password (defaults to deleteme)
    DCOS_ENTERPRISE: determine how to authenticate (defaults to false)
    DCOS_ACS_TOKEN: bypass auth and use the user supplied token
    """
    cluster_url = os.environ.get('CLUSTER_URL')
    dcos_login_username = os.environ.get('DCOS_LOGIN_USERNAME', __CLI_LOGIN_EE_USERNAME)
    dcos_login_password = os.environ.get('DCOS_LOGIN_PASSWORD', __CLI_LOGIN_EE_PASSWORD)
    dcos_enterprise = os.environ.get('DCOS_ENTERPRISE', 'true') == 'true'
    dcos_acs_token = os.environ.get('DCOS_ACS_TOKEN')
    if not dcos_acs_token:
        dcos_acs_token = login(
            dcosurl=cluster_url,
            username=dcos_login_username,
            password=dcos_login_password,
            is_enterprise=dcos_enterprise)
    configure_cli(dcosurl=cluster_url, token=dcos_acs_token)


if __name__ == '__main__':
    logger.setup(os.getenv('TEST_LOG_LEVEL', 'INFO'))
    login_session()
