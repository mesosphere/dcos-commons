#!/usr/bin/python

# Launches a CCM cluster
#
# stdout:
# {'id': ...
#  'url': ...,
#  'auth_token': ...}
#
# cluster.properties file (if WORKSPACE is set in env):
# CLUSTER_ID=...
# CLUSTER_URL=...
# CLUSTER_AUTH_TOKEN=...
#
# Configuration: Mostly through env vars. See README.md.

import json
import logging
import os
import pprint
import random
import string
import subprocess
import sys
import time

import dcos_login
import github_update

try:
    from http.client import HTTPSConnection
except ImportError:
    # Python 2
    from httplib import HTTPSConnection

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class CCMLauncher(object):

    # NOTE: this will need to be updated once 'stable' is no longer 1.7
    _DCOS_17_CHANNELS = ['testing/continuous', 'stable']

    # From mesosphere/cloud-cluster-manager/app/models.py:
    _CCM_STATUSES = {
        0: 'RUNNING',
        3: 'CREATING',
        4: 'DELETING',
        5: 'DELETED',
        6: 'DELETION_FAIL',
        7: 'CREATING_ERROR'
    }
    # Reverse:
    _CCM_STATUS_LABELS = {v: k for k, v in _CCM_STATUSES.items()}

    _CCM_HOST = 'ccm.mesosphere.com'
    _CCM_PATH = '/api/cluster/'


    DEFAULT_TIMEOUT_MINS = 45
    DEFAULT_ATTEMPTS = 2


    def __init__(self, ccm_token, github_label):
        self._http_headers = {'Authorization': 'Token ' + ccm_token}
        self._dry_run = os.environ.get('DRY_RUN', '')
        self._github_updater = github_update.GithubStatusUpdater('cluster:{}'.format(github_label))


    def _rand_str(self, size):
        return ''.join(random.choice(string.ascii_lowercase + string.digits) for _ in range(size))


    def _pretty_time(self, seconds):
        if seconds > 60:
            disp_seconds = seconds % 60
            return '{:.0f}m{:.0f}s'.format((seconds - disp_seconds) / 60, disp_seconds)
        else:
            return '{:.0f}s'.format(seconds)


    def _retry(self, attempts, method, arg, operation_name):
        for i in range(attempts):
            attempt_str = '[{}/{}]'.format(i + 1, attempts)
            try:
                self._github_updater.update('pending', '{} {} in progress'.format(attempt_str, operation_name.title()))
                result = method.__call__(arg)
                self._github_updater.update('success', '{} {} succeeded'.format(attempt_str, operation_name.title()))
                return result
            except Exception as e:
                if i + 1 == attempts:
                    logger.error('{} Final attempt failed, giving up: {}'.format(attempt_str, e))
                    self._github_updater.update('error', '{} {} failed'.format(attempt_str, operation_name.title()))
                    raise
                else:
                    logger.error('{} Previous attempt failed, retrying: {}\n'.format(attempt_str, e))


    def _query_http(self, request_method, request_path,
            request_json_payload=None,
            log_error=True,
            debug=False):
        if self._dry_run:
            logger.info('[DRY RUN] {} https://{}{}'.format(request_method, self._CCM_HOST, request_path))
            if request_json_payload:
                logger.info('[DRY RUN] Payload: {}'.format(pprint.pformat(request_json_payload)))
            return None
        conn = HTTPSConnection(self._CCM_HOST)
        if debug:
            conn.set_debuglevel(999)

        request_headers = self._http_headers.copy()
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
            logger.error('Request: {} https://{}{}'.format(request_method, self._CCM_HOST, request_path))
            logger.error('Response:')
            logger.error('  - Status: {} {}'.format(response.status, str(response.msg).strip()))
            logger.error('  - Headers: {}'.format(pprint.pformat(response.getheaders())))
            logger.error('  - Body: {}'.format(pprint.pformat(response.read())))
            return None
        elif debug:
            logger.debug('{}: {}'.format(response.status, str(response.msg).strip()))
            logger.debug(pprint.pformat(response.getheaders()))
        return response


    def wait_for_status(self, cluster_id, pending_status_label, complete_status_label, timeout_minutes):
        logger.info('Waiting {} minutes for cluster {} to transition from {} to {}'.format(
            timeout_minutes, cluster_id, pending_status_label, complete_status_label))

        pending_state_code = self._CCM_STATUS_LABELS[pending_status_label]
        complete_state_code = self._CCM_STATUS_LABELS[complete_status_label]

        start_time = time.time()
        stop_time = start_time + (60 * timeout_minutes)
        sleep_duration_s = 1
        now = start_time

        while now < stop_time:
            if sleep_duration_s < 32:
                sleep_duration_s *= 2

            response = self._query_http('GET', self._CCM_PATH + str(cluster_id) + '/')
            if response:
                status_json = json.loads(response.read().decode('utf-8'))
                status_code = status_json.get('status', -1)
                status_label = self._CCM_STATUSES.get(status_code, 'unknown:{}'.format(status_code))
                if status_code == complete_state_code:
                    # additional check: does the cluster have a non-empty 'cluster_info'?
                    cluster_info_str = status_json.get('cluster_info', '')
                    if cluster_info_str:
                        # cluster_info in the CCM API is a string containing a dict...:
                        logger.info('Cluster {} has entered state {}, returning cluster_info.'.format(
                            cluster_id, status_label))
                        try:
                            return json.loads(cluster_info_str)
                        except:
                            logger.error('Failed to parse cluster_info string as JSON. Operation failed?: "{}"'.format(cluster_info_str))
                            return None
                    else:
                        logger.error('Cluster {} has entered state {}, but lacks cluster_info...'.format(
                            cluster_id, status_label))
                elif status_code != pending_state_code:
                    logger.error('Cluster {} has entered state {}. Giving up.'.format(
                        cluster_id, status_label))
                    return None

                logger.info('Cluster {} has state {} after {}, refreshing in {}. ({} left)'.format(
                    cluster_id,
                    status_label,
                    self._pretty_time(now - start_time),
                    self._pretty_time(sleep_duration_s),
                    self._pretty_time(stop_time - now)))
            else:
                logger.error('Failed to get cluster {} state after {}, refreshing in {}. ({} left)'.format(
                    cluster_id,
                    self._pretty_time(now - start_time),
                    self._pretty_time(sleep_duration_s),
                    self._pretty_time(stop_time - now)))

            time.sleep(sleep_duration_s)
            now = time.time()

        logger.error('Giving up after {}'.format(self._pretty_time(60 * timeout_minutes)))
        return None


    def start(self, config, attempts = DEFAULT_ATTEMPTS):
        return self._retry(attempts, self._start, config, 'launch')


    def _start(self, config):
        is_17_cluster = config.ccm_channel in self._DCOS_17_CHANNELS
        if is_17_cluster:
            hostrepo = 's3.amazonaws.com/downloads.mesosphere.io/dcos'
        elif config.cf_template.startswith('ee.'):
            hostrepo = 's3.amazonaws.com/downloads.mesosphere.io/dcos-enterprise'
        else:
            hostrepo = 's3-us-west-2.amazonaws.com/downloads.dcos.io/dcos'
        template_url = 'https://{}/{}/cloudformation/{}'.format(
            hostrepo, config.ccm_channel, config.cf_template)
        if config.template_url:
            template_url = config.template_url
        cluster_name = config.name_prefix + self._rand_str(8)
        payload = {
            'template_url': template_url,
            'name': cluster_name,
            'cluster_desc': config.description,
            'time': config.duration_mins,
            'private_agents': str(config.private_agents),
            'public_agents': str(config.public_agents),
            'pre_1_8_cluster': is_17_cluster,
            'adminlocation': config.admin_location,
            'cloud_provider': config.cloud_provider,
            'region': config.aws_region
        }
        logger.info('''Launching cluster:
  name={}
  agents={} private/{} public
  duration={} minutes
  mountvols={}
  permissions={}
  channel={}
  template={}'''.format(
      cluster_name,
      config.private_agents, config.public_agents,
      config.duration_mins,
      config.mount_volumes,
      config.permissions,
      config.ccm_channel,
      config.cf_template))
        response = self._query_http('POST', self._CCM_PATH, request_json_payload=payload)
        if not response:
            raise Exception('CCM cluster creation request failed')
        response_content = response.read().decode('utf-8')
        response_json = json.loads(response_content)
        logger.info('Launch response:\n{}'.format(pprint.pformat(response_json)))
        cluster_id = int(response_json.get('id', 0))
        if not cluster_id:
            raise Exception('No Cluster ID returned in cluster creation response: {}'.format(response_content))
        stack_id = response_json.get('stack_id', '')
        if not stack_id:
            raise Exception('No Stack ID returned in cluster creation response: {}'.format(response_content))

        cluster_info = self.wait_for_status(cluster_id, 'CREATING', 'RUNNING', config.start_timeout_mins)
        if not cluster_info:
            raise Exception('CCM cluster creation failed or timed out')
        dns_address = cluster_info.get('DnsAddress', '')
        if not dns_address:
            raise Exception('CCM cluster_info is missing DnsAddress: {}'.format(cluster_info))
        logger.info('Cluster is now RUNNING: {}'.format(cluster_info))

        if config.mount_volumes:
            logger.info('Enabling mount volumes for cluster {} (stack id {})'.format(cluster_id, stack_id))
            # fabric spams to stdout, which causes problems with launch_ccm_cluster.
            # force total redirect to stderr:
            stdout = sys.stdout
            sys.stdout = sys.stderr
            import enable_mount_volumes
            enable_mount_volumes.main(stack_id)
            sys.stdout = stdout

        # we fetch the token once up-front because on Open clusters it must be reused.
        # given that, we may as well use the same flow across both Open and EE.
        logger.info('Fetching auth token')
        dcos_url = 'https://' + dns_address
        auth_token = dcos_login.DCOSLogin(dcos_url).get_acs_token()

        if config.permissions:
            logger.info('Setting up permissions for cluster {} (stack id {})'.format(cluster_id, stack_id))

            def run_script(scriptname, args = []):
                logger.info('Command: {} {}'.format(scriptname, ' '.join(args)))
                # force total redirect to stderr:
                stdout = sys.stdout
                sys.stdout = sys.stderr
                script_path = os.path.join(os.path.dirname(os.path.realpath(__file__)), scriptname)
                subprocess.check_call(['bash', script_path] + args)
                sys.stdout = stdout

            run_script('create_service_account.sh', [dcos_url, auth_token, '--strict'])
            # Examples of what individual tests should run. See respective projects' "test.sh":
            #run_script('setup_permissions.sh', 'nobody cassandra-role'.split())
            #run_script('setup_permissions.sh', 'nobody hdfs-role'.split())
            #run_script('setup_permissions.sh', 'nobody kafka-role'.split())
            #run_script('setup_permissions.sh', 'nobody spark-role'.split())

        return {
            'id': cluster_id,
            'url': dcos_url,
            'auth_token': auth_token
        }


    def stop(self, config, attempts = DEFAULT_ATTEMPTS):
        return self._retry(attempts, self._stop, config, 'shutdown')


    def trigger_stop(self, config):
        self._stop(config, False)


    def _stop(self, config, wait=True):
        logger.info('Deleting cluster #{}'.format(config.cluster_id))
        response = self._query_http('DELETE', self._CCM_PATH + config.cluster_id + '/')
        if not response:
            raise Exception('CCM cluster deletion request failed')
        if wait:
            cluster_info = self.wait_for_status(config.cluster_id, 'DELETING', 'DELETED', config.stop_timeout_mins)
            if not cluster_info:
                raise Exception('CCM cluster deletion failed or timed out')
            logger.info(pprint.pformat(cluster_info))
        else:
            logger.info('Delete triggered, exiting.')


class StartConfig(object):

    def __init__(
            self,
            name_prefix = 'test-cluster-',
            description = '',
            duration_mins = 240,
            ccm_channel = 'testing/master',
            cf_template = 'ee.single-master.cloudformation.json',
            start_timeout_mins = CCMLauncher.DEFAULT_TIMEOUT_MINS,
            public_agents = 0,
            private_agents = 1,
            aws_region = 'eu-central-1',
            admin_location = '0.0.0.0/0',
            cloud_provider = '0', # https://mesosphere.atlassian.net/browse/TEST-231
            mount_volumes = False,
            permissions = False):
        self.name_prefix = name_prefix
        self.duration_mins = int(os.environ.get('CCM_DURATION_MINS', duration_mins))
        self.ccm_channel = os.environ.get('CCM_CHANNEL', ccm_channel)
        self.cf_template = os.environ.get('CCM_TEMPLATE', cf_template)
        self.start_timeout_mins = int(os.environ.get('CCM_TIMEOUT_MINS', start_timeout_mins))
        self.public_agents = int(os.environ.get('CCM_PUBLIC_AGENTS', public_agents))
        self.private_agents = int(os.environ.get('CCM_AGENTS', private_agents))
        self.aws_region = os.environ.get('CCM_AWS_REGION', aws_region)
        self.admin_location = os.environ.get('CCM_ADMIN_LOCATION', admin_location)
        self.cloud_provider = os.environ.get('CCM_CLOUD_PROVIDER', cloud_provider)
        self.mount_volumes = bool(os.environ.get('CCM_MOUNT_VOLUMES', mount_volumes))
        self.permissions = os.environ.get('SECURITY', '') == 'strict'
        self.template_url = os.environ.get('DCOS_TEMPLATE_URL', None)
        if not description:
            description = 'A test cluster with {} private/{} public agents'.format(
                self.private_agents, self.public_agents)
        self.description = description



class StopConfig(object):
    def __init__(
            self,
            cluster_id,
            stop_timeout_mins = CCMLauncher.DEFAULT_TIMEOUT_MINS):
        self.cluster_id = cluster_id
        self.stop_timeout_mins = os.environ.get('CCM_TIMEOUT_MINS', stop_timeout_mins)


def _write_jenkins_config(github_label, cluster_info, error = None):
    if not 'WORKSPACE' in os.environ:
        return

    # write jenkins properties file to $WORKSPACE/cluster-$CCM_GITHUB_LABEL.properties:
    properties_path = os.path.join(os.environ['WORKSPACE'], 'cluster-{}.properties'.format(github_label))
    logger.info('Writing cluster properties to {}'.format(properties_path))
    properties_file = open(properties_path, 'w')
    properties_file.write('CLUSTER_ID={}\n'.format(cluster_info.get('id', '0')))
    properties_file.write('CLUSTER_URL={}\n'.format(cluster_info.get('url', '')))
    properties_file.write('CLUSTER_AUTH_TOKEN={}\n'.format(cluster_info.get('auth_token', '')))
    if error:
        properties_file.write('ERROR={}\n'.format(error))
    properties_file.flush()
    properties_file.close()


def main(argv):
    ccm_token = os.environ.get('CCM_AUTH_TOKEN', '')
    if not ccm_token:
        raise Exception('CCM_AUTH_TOKEN is required')

    # used for status and for jenkins .properties file:
    github_label = os.environ.get('CCM_GITHUB_LABEL', '')
    if not github_label:
        github_label = os.environ.get('TEST_GITHUB_LABEL', 'ccm')

    # error detection (and retry) for either a start or a stop operation:
    start_stop_attempts = int(os.environ.get('CCM_ATTEMPTS', CCMLauncher.DEFAULT_ATTEMPTS))

    launcher = CCMLauncher(ccm_token, github_label)
    if len(argv) >= 2:
        if argv[1] == 'stop':
            if len(argv) >= 3:
                launcher.stop(StopConfig(argv[2]), start_stop_attempts)
                return 0
            else:
                logger.info('Usage: {} stop <ccm_id>'.format(argv[0]))
                return 1
        if argv[1] == 'trigger-stop':
            if len(argv) >= 3:
                launcher.trigger_stop(StopConfig(argv[2]))
                return 0
            else:
                logger.info('Usage: {} trigger-stop <ccm_id>'.format(argv[0]))
                return 1
        if argv[1] == 'wait':
            if len(argv) >= 5:
                # piggy-back off of StopConfig's env handling:
                stop_config = StopConfig(argv[2])
                cluster_info = launcher.wait_for_status(
                    stop_config.cluster_id, argv[3], argv[4], stop_config.stop_timeout_mins)
                if not cluster_info:
                    return 1
                # print to stdout (the rest of this script only writes to stderr):
                print(pprint.pformat(cluster_info))
                return 0
            else:
                logger.info('Usage: {} wait <ccm_id> <current_state> <new_state>'.format(argv[0]))
                return 1
        else:
            logger.info('Usage: {} [stop <ccm_id>|trigger-stop <ccm_id>|wait <ccm_id> <current_state> <new_state>]'.format(argv[0]))
            return

    try:
        cluster_info = launcher.start(StartConfig(), start_stop_attempts)
        # print to stdout (the rest of this script only writes to stderr):
        print(json.dumps(cluster_info))
        _write_jenkins_config(github_label, cluster_info)
    except Exception as e:
        _write_jenkins_config(github_label, {}, e)
        raise
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
