#!/usr/bin/python

# Launch a CCM cluster
#
# stdout:
# {'id': ...
#  'url': ...}

import json
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
        self.__dcos_url = dcos_url.rstrip('/')
        self.__token_open = token_open
        self.__user_ee = user_ee
        self.__password_ee = password_ee


    def __query_http(
            self,
            request_method,
            request_path,
            request_json_payload=None,
            log_error=True,
            debug=False):
        parsed_url = urlparse(self.__dcos_url)
        if parsed_url.scheme == 'https':
            # EE clusters are often self-signed, disable validation:
            conn = HTTPSConnection(parsed_url.hostname, context=ssl._create_unverified_context())
        elif parsed_url.scheme == 'http':
            conn = HTTPConnection(parsed_url.hostname)
        else:
            raise Exception('Unsupported protocol: {} (from url={})'.format(
                parsed_url.scheme, self.__dcos_url))
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
            print('Got {} response to HTTP request:'.format(response.status))
            print('Request: {} {}'.format(request_method, request_path))
            print('Response: {} {}'.format(response.status, str(response.msg).strip()))
            pprint.pprint(response.getheaders())
            pprint.pprint(response.read())
            return None
        elif debug:
            print('{}: {}'.format(response.status, str(response.msg).strip()))
            pprint.pprint(response.getheaders())
        return response


    def __is_enterprise_cluster(self, debug):
        # the main auth/login endpoint doesn't return the 'www-authenticate' header unless we
        # actually attempt a login (fully populated json), so use a different arbitrary endpoint:
        response = self.__query_http('GET', '/acs/api/v1/groups', log_error=False, debug=debug)
        if not response.status == 401:
            raise Exception('Expected 401 error for detection request, got {}', response.status)
        auth_type = ''
        for entry in response.getheaders():
            if entry[0].lower() == 'www-authenticate':
                auth_type = entry[1]
                break
        if auth_type == 'oauthjwt':
            if debug:
                print('Autodetected DC/OS Open')
            return False
        elif auth_type == 'acsjwt':
            if debug:
                print('Autodetected DC/OS Enterprise')
            return True
        else:
            raise Exception('Unknown authentication method in response headers: {}'.format(
                response.getheaders()))

    def get_acs_token(self, debug=False):
        if self.__is_enterprise_cluster(debug):
            payload = {'uid': self.__user_ee, 'password': self.__password_ee}
        else:
            payload = {'token': self.__token_open}
        response = self.__query_http(
            'POST', '/acs/api/v1/auth/login', request_json_payload=payload, debug=debug)
        if not response:
            raise Exception('Failed to authenticate with cluster at {}'.format(self.__dcos_url))
        return json.loads(response.read().decode('utf-8'))['token']


    def login(self, debug=False):
        token = self.get_acs_token(debug)
        ret = subprocess.Popen(
            'dcos config set core.dcos_acs_token {}'.format(token).split(' '),
            stdout=subprocess.PIPE, stderr=subprocess.PIPE)


def main(argv):
    ret = subprocess.Popen(
        'dcos config show core.dcos_url'.split(' '),
        stdout=subprocess.PIPE)
    dcos_url = ret.stdout.readline().decode('utf-8').strip()
    login = DCOSLogin(dcos_url)
    if len(argv) >= 2 and argv[1] == "token":
        print(login.get_acs_token())
    else:
        print('Logging in to: {}'.format(dcos_url))
        DCOSLogin(dcos_url).login()
        print('Login successful. Access token with: dcos config show core.dcos_acs_token')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
