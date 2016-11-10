import json
import os
import time
from functools import wraps

import dcos
import requests
import shakedown

DEFAULT_HTTP_PORT = 9200

PACKAGE_NAME = 'elastic'
WAIT_TIME_IN_SECONDS = 1200
DEFAULT_TASK_COUNT = 8
DEFAULT_NODE_COUNT = 7
DEFAULT_INDEX_NAME = 'customer'
DEFAULT_INDEX_TYPE = 'entry'
DCOS_URL = shakedown.run_dcos_command('config show core.dcos_url')[0].strip()
DCOS_TOKEN = shakedown.run_dcos_command('config show core.dcos_acs_token')[0].strip()

TASK_RUNNING_STATE = 'TASK_RUNNING'

REQUEST_HEADERS = {
    'authorization': 'token=%s' % DCOS_TOKEN
}

OPTIONS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'options')
COMPLETE_CLUSTER_OPTIONS_FILE = os.path.join(OPTIONS_DIR, 'complete.json')


def as_json(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            return json.loads(fn(*args, **kwargs))
        except ValueError:
            return None

    return wrapper


def check_dcos_service_health():
    return shakedown.service_healthy(PACKAGE_NAME)


def wait_for_dcos_tasks_health(task_count):
    def fn():
        try:
            return shakedown.get_service_tasks(PACKAGE_NAME)
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(tasks):
        running_tasks = [t for t in tasks if t['state'] == TASK_RUNNING_STATE]
        print('Waiting for {} healthy tasks, got {}/{}'.format(
            task_count, len(running_tasks), len(tasks)))
        return (
            len(running_tasks) == task_count,
            'Service did not become healthy'
        )

    return spin(fn, success_predicate)


def check_elasticsearch_index_health(index_name, color, http_port=DEFAULT_HTTP_PORT):
    def fn():
        return get_elasticsearch_index_health(index_name, http_port)

    def success_predicate(result):
        return (
            result and result["status"] == color, 'Index did not reach {}'.format(color)
        )

    return spin(fn, success_predicate)


def wait_for_expected_nodes_to_exist():
    def fn():
        return get_elasticsearch_cluster_health()

    def success_predicate(result):
        return (
            result and result["number_of_nodes"] == DEFAULT_NODE_COUNT,
            'Cluster did not reach {} nodes'.format(DEFAULT_NODE_COUNT)
        )

    return spin(fn, success_predicate)


def check_plugin_installed(plugin_name):
    def fn():
        return get_hosts_with_plugin(plugin_name)

    def success_predicate(result):
        return (
            result is not None and len(result) == DEFAULT_NODE_COUNT, 'Plugin {} was not installed'.format(plugin_name)
        )

    return spin(fn, success_predicate)


def check_plugin_uninstalled(plugin_name):
    def fn():
        return get_hosts_with_plugin(plugin_name)

    def success_predicate(result):
        return (
            result is not None and result == [], 'Plugin {} was not uninstalled'.format(plugin_name)
        )

    return spin(fn, success_predicate)


def check_new_elasticsearch_master_elected(initial_master):
    def fn():
        return get_elasticsearch_master()

    def success_predicate(result):
        return (
            result.startswith("master") and result != initial_master, 'New master not reelected'
        )

    return spin(fn, success_predicate)


def marathon_update(config):
    request(requests.put, marathon_api_url('apps/{}'.format(PACKAGE_NAME)), json=config, headers=REQUEST_HEADERS,
            verify=False)


def task_ids_dont_change(initial_task_ids):
    def fn():
        try:
            return initial_task_ids == get_task_ids()
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(tasks):
        return (initial_task_ids == get_task_ids(), "Task IDs changed")

    return (len(tasks) == 1 and tasks[0]['id'] != task_id, "Task ID didn't change.")

    return spin(fn, success_predicate)


def get_task_ids():
    tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    return [t['id'] for t in tasks]


def request(request_fn, *args, **kwargs):
    def success_predicate(response):
        return (
            response.status_code == 200,
            'Request failed: %s' % response.content,
        )

    return spin(request_fn, success_predicate, *args, **kwargs)


def get_elasticsearch_master():
    exit_status, output = shakedown.run_command_on_master("{}/_cat/master'".format(curl_api("GET")))
    master = output.split()[-1]
    return master


def get_hosts_with_plugin(plugin_name):
    exit_status, output = shakedown.run_command_on_master("{}/_cat/plugins'".format(curl_api("GET")))
    if exit_status:
        return [host for host in output.split("\n") if plugin_name in host]
    else:
        return None


@as_json
def get_elasticsearch_index_health(index_name, http_port=DEFAULT_HTTP_PORT):
    exit_status, output = shakedown.run_command_on_master(
        "{}/_cluster/health/{}'".format(curl_api("GET", http_port), index_name))
    return output


@as_json
def get_elasticsearch_cluster_health():
    exit_status, output = shakedown.run_command_on_master("{}/_cluster/health'".format(curl_api("GET")))
    return output


@as_json
def get_elasticsearch_indices_stats(index_name):
    exit_status, output = shakedown.run_command_on_master("{}/{}/_stats'".format(curl_api("GET"), index_name))
    return output


@as_json
def create_index(index_name, params):
    command = "{}/{}' -d '{}'".format(curl_api("PUT"), index_name, json.dumps(params))
    exit_status, output = shakedown.run_command_on_master(command)
    return output


@as_json
def graph_api(index_name, query):
    command = "{}/{}/_graph/explore' -d '{}'".format(curl_api("POST"), index_name, json.dumps(query))
    exit_status, output = shakedown.run_command_on_master(command)
    return output


@as_json
def delete_index(index_name):
    exit_status, output = shakedown.run_command_on_master("{}/{}'".format(curl_api("DELETE"), index_name))
    return output


@as_json
def create_document(index_name, index_type, doc_id, params):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/{}/{}' -d '{}'".format(curl_api("PUT"), index_name, index_type, doc_id, json.dumps(params)))
    return output


@as_json
def get_document(index_name, index_type, doc_id):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/{}/{}'".format(curl_api("GET"), index_name, index_type, doc_id))
    return output


def spin(fn, success_predicate, *args, **kwargs):
    end_time = time.time() + WAIT_TIME_IN_SECONDS
    while time.time() < end_time:
        try:
            result = fn(*args, **kwargs)
        except Exception as e:
            is_successful, error_message = False, str(e)
        else:
            is_successful, error_message = success_predicate(result)

        if is_successful:
            print('Success state reached, exiting spin. prev_err={}'.format(error_message))
            break
        print('Waiting for success state... err={}'.format(error_message))
        time.sleep(1)

    assert is_successful, error_message

    return result


def uninstall():
    print('Uninstalling/janitoring {}'.format(PACKAGE_NAME))
    try:
        shakedown.uninstall_package_and_wait(PACKAGE_NAME, app_id=PACKAGE_NAME)
    except (dcos.errors.DCOSException, ValueError) as e:
        print('Got exception when uninstalling package, continuing with janitor anyway: {}'.format(e))

    shakedown.run_command_on_master(
        "docker run mesosphere/janitor /janitor.py -r "
        "{}-role -p {}-principal -z dcos-service-{} --auth_token={}".format(PACKAGE_NAME, PACKAGE_NAME, PACKAGE_NAME,
                                                                            DCOS_TOKEN)
    )


def get_elasticsearch_config():
    response = requests.get(marathon_api_url('apps/{}/versions'.format(PACKAGE_NAME)), headers=REQUEST_HEADERS,
                            verify=False)
    assert response.status_code == 200, 'Marathon versions request failed'

    version = response.json()['versions'][0]

    response = requests.get(marathon_api_url('apps/{}/versions/{}'.format(PACKAGE_NAME, version)),
                            headers=REQUEST_HEADERS,
                            verify=False)
    assert response.status_code == 200

    config = response.json()
    del config['uris']
    del config['version']

    return config


def marathon_api_url(basename):
    return '{}/v2/{}'.format(shakedown.dcos_service_url('marathon'), basename)


def curl_api(method, http_port=DEFAULT_HTTP_PORT):
    return "curl -X{} -s -u elastic:changeme 'http://master-0.{}.mesos:{}".format(method, PACKAGE_NAME, http_port)


def get_marathon_host():
    return shakedown.get_marathon_tasks()[0]['statuses'][0]['container_status']['network_infos'][0]['ip_addresses'][0][
        'ip_address']
