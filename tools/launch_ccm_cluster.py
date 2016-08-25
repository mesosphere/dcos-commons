#!/usr/bin/python

import json
import os
import pprint
import random
import string
import sys
import time

try:
    from http.client import HTTPSConnection
except ImportError:
    # Python 2
    from httplib import HTTPSConnection

class CCMLauncher(object):

    __DCOS_17_CHANNELS = ['testing/continuous', 'stable']

    # From mesosphere/cloud-cluster-manager/app/models.py:
    __CCM_STATUSES = {
        0: 'RUNNING',
        3: 'CREATING',
        4: 'DELETING',
        5: 'DELETED',
        6: 'DELETION_FAIL',
        7: 'CREATING_ERROR'
    }
    # Reverse:
    __CCM_STATUS_LABELS = {v: k for k, v in __CCM_STATUSES.items()}

    __CCM_HOST = 'ccm.mesosphere.com'
    __CCM_PATH = '/api/cluster/'


    def __init__(self, ccm_token):
        self.__http_headers = {'Authorization': 'Token ' + ccm_token}


    def __pretty_time(self, seconds):
        if seconds > 60:
            disp_seconds = seconds % 60
            return '{:.0f}m{:.0f}s'.format((seconds - disp_seconds) / 60, disp_seconds)
        else:
            return '{:.0f}s'.format(seconds)


    def __query_http(self, request_method, request_path,
            request_json_payload=None,
            log_error=True,
            debug=False):
        conn = HTTPSConnection(self.__CCM_HOST)
        if debug:
            conn.set_debuglevel(999)

        request_headers = self.__http_headers.copy()
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


    def wait_for_status(self, cluster_id, pending_status_label, complete_status_label, timeout_minutes):
        print('Waiting {} minutes for cluster {} to go from {} to {}'.format(
            timeout_minutes, cluster_id, pending_status_label, complete_status_label))

        pending_state_code = self.__CCM_STATUS_LABELS[pending_status_label]
        complete_state_code = self.__CCM_STATUS_LABELS[complete_status_label]

        start_time = time.time()
        stop_time = start_time + (60 * timeout_minutes)
        sleep_duration_s = 1
        now = start_time

        while now < stop_time:
            if sleep_duration_s < 32:
                sleep_duration_s *= 2

            response = self.__query_http('GET', self.__CCM_PATH + str(cluster_id) + '/')
            if response:
                status_json = json.loads(response.read())
                status_code = status_json.get('status', -1)
                status_label = self.__CCM_STATUSES.get(status_code, 'unknown:{}'.format(status_code))
                if status_code == complete_state_code:
                    # additional check: does the cluster have a non-empty 'cluster_info'?
                    cluster_info_str = status_json.get('cluster_info', '')
                    if cluster_info_str:
                        # cluster_info in the CCM API is a string containing a dict...:
                        print('Cluster {} has entered state {}, returning cluster_info.'.format(
                            cluster_id, status_label))
                        try:
                            return json.loads(cluster_info_str)
                        except:
                            print('Failed to parse cluster_info string as JSON. Operation failed?: "{}"'.format(cluster_info_str))
                            return None
                    else:
                        print('Cluster {} has entered state {}, but lacks cluster_info...'.format(
                            cluster_id, status_label))
                elif status_code != pending_state_code:
                    print('Cluster {} has entered state {}. Giving up.'.format(
                        cluster_id, status_label))
                    return None

                print('Cluster {} has state {} after {}, refreshing in {}. ({} left)'.format(
                    cluster_id,
                    status_label,
                    self.__pretty_time(now - start_time),
                    self.__pretty_time(sleep_duration_s),
                    self.__pretty_time(stop_time - now)))
            else:
                print('Failed to get cluster {} state after {}, refreshing in {}. ({} left)'.format(
                    cluster_id,
                    self.__pretty_time(now - start_time),
                    self.__pretty_time(sleep_duration_s),
                    self.__pretty_time(stop_time - now)))

            time.sleep(sleep_duration_s)
            now = time.time()

        print('Giving up after {}'.format(self.__pretty_time(60 * timeout_minutes)))
        return None


    def start(
            self,
            name,
            description,
            time_mins,
            ccm_channel,
            cf_template,
            start_timeout_mins = 45,
            public_agents = 0,
            private_agents = 1,
            aws_region = 'us-west-2',
            admin_location = '0.0.0.0/0',
            cloud_provider = '0'): # https://mesosphere.atlassian.net/browse/TEST-231
        is_17_cluster = ccm_channel in self.__DCOS_17_CHANNELS
        if is_17_cluster:
            hostrepo = 's3.amazonaws.com/downloads.mesosphere.io/dcos'
        elif cf_template.startswith('ee.'):
            hostrepo = 's3.amazonaws.com/downloads.mesosphere.io/dcos-enterprise'
        else:
            hostrepo = 's3-us-west-2.amazonaws.com/downloads.dcos.io/dcos'
        template_url = 'https://{}/{}/cloudformation/{}'.format(hostrepo, ccm_channel, cf_template)

        payload = {
            'template_url': template_url,
            'name': name,
            'cluster_desc': description,
            'time': time_mins,
            'private_agents': str(private_agents),
            'public_agents': str(public_agents),
            'pre_1_8_cluster': is_17_cluster,
            'adminlocation': admin_location,
            'cloud_provider': cloud_provider,
            'region': aws_region
        }
        print('Launching cluster named "{}" with {} private/{} public agents for {} minutes against template at {}'.format(
            name, private_agents, public_agents, time_mins, template_url))
        response = self.__query_http('POST', self.__CCM_PATH, request_json_payload=payload)
        if not response:
            raise Exception('CCM cluster creation request failed')
        response_content = response.read()
        cluster_id = int(json.loads(response_content).get('id', 0))
        if not cluster_id:
            raise Exception('No ID returned in cluster creation response: {}'.format(response_content))
        cluster_info = self.wait_for_status(cluster_id, 'CREATING', 'RUNNING', start_timeout_mins)
        if not cluster_info:
            raise Exception('CCM cluster creation failed or timed out')
        dns_address = cluster_info.get('DnsAddress', '')
        if not dns_address:
            raise Exception('CCM cluster_info is missing DnsAddress: {}'.format(cluster_info))
        return {
            'id': cluster_id,
            'url': 'https://' + dns_address}


    def stop(self, cluster_id, stop_timeout_mins = 10):
        response = self.__query_http('DELETE', self.__CCM_PATH + cluster_id + '/')
        if not response:
            raise Exception('CCM cluster deletion request failed')
        cluster_info = self.wait_for_status(cluster_id, 'DELETING', 'DELETED', stop_timeout_mins)
        if not cluster_info:
            raise Exception('CCM cluster deletion failed or timed out')
        pprint.pprint(cluster_info)

def __rand_str(size):
    return ''.join(random.choice(string.ascii_lowercase + string.digits) for _ in range(size))

def main(argv):
    ccm_token = os.environ.get('CCM_AUTH_TOKEN', '')
    if not ccm_token:
        raise Exception('CCM_AUTH_TOKEN is required')

    launcher = CCMLauncher(ccm_token)
    timeout_mins = int(os.environ.get('CCM_TIMEOUT_MINUTES', 45))

    if len(argv) >= 2:
        if argv[1] == 'stop':
            if len(argv) >= 3:
                launcher.stop(argv[2], timeout_mins)
                return 0
            else:
                print('Usage: {} stop <ccm_id>'.format(argv[0]))
                return 1
        if argv[1] == 'wait':
            if len(argv) >= 5:
                cluster_info = launcher.wait_for_status(argv[2], argv[3], argv[4], timeout_mins)
                if not cluster_info:
                    return 1
                pprint.pprint(cluster_info)
                return 0
            else:
                print('Usage: {} wait <ccm_id> <current_state> <new_state>'.format(argv[0]))
                return 1
        else:
            print('Usage: {} [stop <ccm_id>|wait <ccm_id> <current_state> <new_state>]'.format(argv[0]))
            return

    public_agents = int(os.environ.get('CCM_PUBLIC_AGENTS', '0'))
    private_agents = int(os.environ.get('CCM_AGENTS', '1'))

    cluster_name = os.environ.get('CCM_CLUSTER_NAME', 'test-cluster-' + __rand_str(8))
    cluster_description = os.environ.get(
        'CCM_CLUSTER_DESCRIPTION',
        'A test cluster with {} private/{} public agents'.format(
            private_agents, public_agents))

    time_mins = int(os.environ.get('CCM_CLUSTER_MINUTES', '60'))

    ccm_channel = os.environ.get('CCM_CHANNEL', 'testing/master')
    cf_template = os.environ.get('CCM_TEMPLATE', 'ee.single-master.cloudformation.json')

    cluster_id_url = launcher.start(
        name=cluster_name,
        description=cluster_description,

        time_mins=time_mins,

        ccm_channel=ccm_channel,
        cf_template=cf_template,

        start_timeout_mins=timeout_mins,

        public_agents=public_agents,
        private_agents=private_agents)
    pprint.pprint(cluster_id_url)
    return 0

if __name__ == '__main__':
    sys.exit(main(sys.argv))
