import logging
import os

import requests
import shakedown

__CLI_LOGIN_OPEN_TOKEN = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik9UQkVOakZFTWtWQ09VRTRPRVpGTlRNMFJrWXlRa015Tnprd1JrSkVRemRCTWpBM1FqYzVOZyJ9.eyJlbWFpbCI6ImFsYmVydEBiZWtzdGlsLm5ldCIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJpc3MiOiJodHRwczovL2Rjb3MuYXV0aDAuY29tLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTA5OTY0NDk5MDExMTA4OTA1MDUwIiwiYXVkIjoiM3lGNVRPU3pkbEk0NVExeHNweHplb0dCZTlmTnhtOW0iLCJleHAiOjIwOTA4ODQ5NzQsImlhdCI6MTQ2MDE2NDk3NH0.OxcoJJp06L1z2_41_p65FriEGkPzwFB_0pA9ULCvwvzJ8pJXw9hLbmsx-23aY2f-ydwJ7LSibL9i5NbQSR2riJWTcW4N7tLLCCMeFXKEK4hErN2hyxz71Fl765EjQSO5KD1A-HsOPr3ZZPoGTBjE0-EFtmXkSlHb1T2zd0Z8T5Z2-q96WkFoT6PiEdbrDA-e47LKtRmqsddnPZnp0xmMQdTr2MjpVgvqG7TlRvxDcYc-62rkwQXDNSWsW61FcKfQ-TRIZSf2GS9F9esDF4b5tRtrXcBNaorYa9ql0XAWH5W_ct4ylRNl3vwkYKWa4cmPvOqT5Wlj9Tf0af4lNO40PQ'
__CLI_LOGIN_EE_USERNAME = 'bootstrapuser'
__CLI_LOGIN_EE_PASSWORD = 'deleteme'

log = logging.getLogger(__name__)


def get_acs_token(dcosurl: str, username: str, password: str, is_enterprise: bool) -> None:
    if is_enterprise:
        log.info('logging into {} as {}'.format(dcosurl, username))
        payload = {'uid': username, 'password': password}
    else:
        log.info('logging into {} with default open token'.format(dcosurl))
        payload = {'token': __CLI_LOGIN_OPEN_TOKEN}

    headers = {'Content-Type': 'application/json',}
    login_endpoint = '{dcosurl}/acs/api/v1/auth/login'.format(dcosurl=dcosurl)
    r = requests.post(login_endpoint, headers=headers, json=payload, verify=False)
    assert r.status_code == 200, '{} failed {}: {}'.format(login_endpoint, r.status_code, r.text)

    return r.json()['token']


def login(dcosurl: str, username: str, password: str, is_enterprise: bool, user_token: str) -> str:
    if not user_token:
        user_token = get_acs_token(dcosurl, username, password, is_enterprise)
    return user_token


def configure_cli(dcosurl: str, token: str) -> None:
    out, err, rc = shakedown.run_dcos_command('dcos config set core.dcos_url {}'.format(dcosurl))
    assert rc, 'Failed to set core.dcos_url: {}'.format(err)
    out, err, rc = shakedown.run_dcos_command('dcos config set core.ssl_verify false')
    assert rc, 'Failed to set core.ssl_verify: {}'.format(err)
    out, err, rc = shakedown.run_dcos_command('config set core.dcos_acs_token {}'.format(token))
    assert rc, 'Failed to set core.dcos_acs_token: {}'.format(err)


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

    This should generally be used as a fixture in a framework's conftest.py:

    @pytest.fixture(scope='session')
    def configure_login():
        yield from sdk_login.login_session()
    """
    try:
        cluster_url = os.environ.get('CLUSTER_URL')
        dcos_login_username = os.environ.get('DCOS_LOGIN_USERNAME', __CLI_LOGIN_EE_USERNAME)
        dcos_login_password = os.environ.get('DCOS_LOGIN_PASSWORD', __CLI_LOGIN_EE_PASSWORD)
        dcos_enterprise = os.environ.get('DCOS_ENTERPRISE', False) and True
        dcos_acs_token = os.environ.get('DCOS_ACS_TOKEN')
        token = login(
            dcosurl=cluster_url,
            username=dcos_login_username,
            password=dcos_login_password,
            is_enterprise=dcos_enterprise,
            user_token=dcos_acs_token
        )
        configure_cli(dcosurl, token)
        yield
    finally:
        logout(dcosurl=cluster_url)
