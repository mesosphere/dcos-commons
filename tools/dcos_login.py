#!/usr/bin/python

# Log in to a cluster using default credentials.
#
# On success: CLI in PATH is logged in and zero is returned
# On failure: non-zero is returned
#
# Configuration:
# - DCOS_TOKEN: Custom core.dcos_acs_token to use, instead of default credentials

import json
import logging
import os
import pprint
import subprocess
import sys
import tempfile

try:
    from http.client import HTTPConnection, HTTPSConnection, ssl
    from urllib.parse import urlparse
except ImportError:
    # Python 2
    from httplib import HTTPConnection, HTTPSConnection, ssl
    from urlparse import urlparse

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class DCOSLogin(object):

    __CLI_LOGIN_OPEN_TOKEN = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik9UQkVOakZFTWtWQ09VRTRPRVpGTlRNMFJrWXlRa015Tnprd1JrSkVRemRCTWpBM1FqYzVOZyJ9.eyJlbWFpbCI6ImFsYmVydEBiZWtzdGlsLm5ldCIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJpc3MiOiJodHRwczovL2Rjb3MuYXV0aDAuY29tLyIsInN1YiI6Imdvb2dsZS1vYXV0aDJ8MTA5OTY0NDk5MDExMTA4OTA1MDUwIiwiYXVkIjoiM3lGNVRPU3pkbEk0NVExeHNweHplb0dCZTlmTnhtOW0iLCJleHAiOjIwOTA4ODQ5NzQsImlhdCI6MTQ2MDE2NDk3NH0.OxcoJJp06L1z2_41_p65FriEGkPzwFB_0pA9ULCvwvzJ8pJXw9hLbmsx-23aY2f-ydwJ7LSibL9i5NbQSR2riJWTcW4N7tLLCCMeFXKEK4hErN2hyxz71Fl765EjQSO5KD1A-HsOPr3ZZPoGTBjE0-EFtmXkSlHb1T2zd0Z8T5Z2-q96WkFoT6PiEdbrDA-e47LKtRmqsddnPZnp0xmMQdTr2MjpVgvqG7TlRvxDcYc-62rkwQXDNSWsW61FcKfQ-TRIZSf2GS9F9esDF4b5tRtrXcBNaorYa9ql0XAWH5W_ct4ylRNl3vwkYKWa4cmPvOqT5Wlj9Tf0af4lNO40PQ'

    __CLI_LOGIN_EE_USERNAME = 'bootstrapuser'
    __CLI_LOGIN_EE_PASSWORD = 'deleteme'

    def __init__(
            self,
            dcos_url,
            token_open=__CLI_LOGIN_OPEN_TOKEN,
            user_ee=__CLI_LOGIN_EE_USERNAME,
            password_ee=__CLI_LOGIN_EE_PASSWORD):
        self._dcos_url = dcos_url.rstrip('/')
        self._token_open = token_open
        self._user_ee = user_ee
        self._password_ee = password_ee
        self._cached_token = ''


    def _query_http(
            self,
            request_method,
            request_path,
            request_json_payload=None,
            log_error=True,
            debug=False):
        parsed_url = urlparse(self._dcos_url)
        if parsed_url.scheme == 'https':
            # EE clusters are often self-signed, disable validation:
            conn = HTTPSConnection(parsed_url.hostname, context=ssl._create_unverified_context())
        elif parsed_url.scheme == 'http':
            conn = HTTPConnection(parsed_url.hostname)
        else:
            raise Exception('Unsupported protocol: {} (from url={})'.format(
                parsed_url.scheme, self._dcos_url))
        if debug:
            conn.set_debuglevel(999)

        request_headers = {}
        if request_json_payload:
            request_body = json.dumps(request_json_payload).encode('utf-8')
            request_headers['Content-Type'] = 'application/json'
        else:
            request_body = None
        conn.request(
            request_method,
            request_path,
            body = request_body,
            headers = request_headers)
        response = conn.getresponse()
        if log_error and (response.status < 200 or response.status >= 300):
            logger.error('Got {} response to HTTP request:'.format(response.status))
            logger.error('Request: {} {}'.format(request_method, request_path))
            logger.error('Response: {} {}'.format(response.status, str(response.msg).strip()))
            logger.error(pprint.pformat(response.getheaders()))
            logger.error(pprint.pformat(response.read()))
            return None
        elif debug:
            logger.debug('{}: {}'.format(response.status, str(response.msg).strip()))
            logger.debug(pprint.pformat(response.getheaders()))
        return response


    def is_enterprise_cluster(self, debug):
        # the main auth/login endpoint doesn't return the 'www-authenticate' header unless we
        # actually attempt a login (fully populated json), so use a different arbitrary endpoint:
        response = self._query_http('GET', '/acs/api/v1/groups', log_error=False, debug=debug)
        if not response.status == 401:
            raise Exception('Expected 401 error for detection request, got {}', response.status)
        auth_type = ''
        for entry in response.getheaders():
            if entry[0].lower() == 'www-authenticate':
                auth_type = entry[1]
                break
        if auth_type == 'oauthjwt':
            if debug:
                logger.debug('Autodetected DC/OS Open')
            return False
        elif auth_type == 'acsjwt':
            if debug:
                logger.debug('Autodetected DC/OS Enterprise')
            return True
        else:
            raise Exception('Unknown authentication method in response headers: {}'.format(
                response.getheaders()))

    def get_acs_token(self, debug=False):
        env_token = os.environ.get('CLUSTER_AUTH_TOKEN', '')
        if env_token:
            return env_token
        if self._cached_token:
            return self._cached_token

        if self.is_enterprise_cluster(debug):
            payload = {'uid': self._user_ee, 'password': self._password_ee}
        else:
            payload = {'token': self._token_open}
        response = self._query_http(
            'POST', '/acs/api/v1/auth/login', request_json_payload=payload, debug=debug)
        if not response:
            raise Exception('Failed to authenticate with cluster at {}'.format(self._dcos_url))

        self._cached_token = json.loads(response.read().decode('utf-8'))['token']
        return self._cached_token


def main(argv):
    dcos_url = os.environ.get('CLUSTER_URL', '')
    if not dcos_url:
        # get url from dcos CLI:
        ret = subprocess.Popen(
            'dcos config show core.dcos_url'.split(' '),
            stdout=subprocess.PIPE)
        dcos_url = ret.stdout.readline().decode('utf-8').strip()
    # do handshake:
    login = DCOSLogin(dcos_url)
    logger.info('Logging in to: {}'.format(dcos_url))
    if len(argv) >= 2 and argv[1] == "print":
        # use stdout. the rest of the code uses stderr via 'logger'
        print(login.get_acs_token())
    else:
        subprocess.check_call(
            'dcos config set core.dcos_acs_token {}'.format(self.get_acs_token(debug)).split(' '))
        logger.info('Login successful. Access token with: dcos config show core.dcos_acs_token')
        logger.info('(Or call this script with "print" to also print token to stdout)')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
