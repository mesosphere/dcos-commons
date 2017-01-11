import json
from functools import wraps

import dcos
import requests
import shakedown

PACKAGE_NAME = 'elastic'
WAIT_TIME_IN_SECONDS = 900
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


def gc_frameworks():
    for host in shakedown.get_private_agents():
        shakedown.run_command(host, "sudo rm -rf /var/lib/mesos/slave/slaves/*/frameworks/*")


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


def tasks_running_success_predicate(task_count):
    tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    running_tasks = [t for t in tasks if t['state'] == TASK_RUNNING_STATE]
    print('Waiting for {} healthy tasks, got {}/{}'.format(task_count, len(running_tasks), len(tasks)))
    return len(running_tasks) == task_count


def wait_for_dcos_tasks_health(task_count):
    return shakedown.wait_for(lambda: tasks_running_success_predicate(task_count), timeout_seconds=WAIT_TIME_IN_SECONDS)


def index_health_success_predicate(index_name, color):
    result = get_elasticsearch_index_health(index_name)
    return result and result["status"] == color


def check_elasticsearch_index_health(index_name, color):
    return shakedown.wait_for(lambda: index_health_success_predicate(index_name, color),
                              timeout_seconds=WAIT_TIME_IN_SECONDS)


def expected_nodes_success_predicate():
    result = get_elasticsearch_cluster_health()
    if result is None:
        return False
    node_count = result["number_of_nodes"]
    print('Waiting for {} healthy nodes, got {}'.format(node_count, DEFAULT_NODE_COUNT))
    return node_count == DEFAULT_NODE_COUNT


def wait_for_expected_nodes_to_exist():
    return shakedown.wait_for(lambda: expected_nodes_success_predicate(), timeout_seconds=WAIT_TIME_IN_SECONDS)


def plugins_installed_success_predicate(plugin_name):
    result = get_hosts_with_plugin(plugin_name)
    return result is not None and len(result) == DEFAULT_NODE_COUNT


def check_plugin_installed(plugin_name):
    return shakedown.wait_for(lambda: plugins_installed_success_predicate(plugin_name),
                              timeout_seconds=WAIT_TIME_IN_SECONDS)


def plugins_uninstalled_success_predicate(plugin_name):
    result = get_hosts_with_plugin(plugin_name)
    return result is not None and result == []


def check_plugin_uninstalled(plugin_name):
    return shakedown.wait_for(lambda: plugins_uninstalled_success_predicate(plugin_name),
                              timeout_seconds=WAIT_TIME_IN_SECONDS)


def new_master_elected_success_predicate(initial_master):
    result = get_elasticsearch_master()
    return result.startswith("master") and result != initial_master


def check_new_elasticsearch_master_elected(initial_master):
    return shakedown.wait_for(lambda: new_master_elected_success_predicate(initial_master),
                              timeout_seconds=WAIT_TIME_IN_SECONDS)


def marathon_update(config):
    requests.put(marathon_api_url('apps/{}'.format(PACKAGE_NAME)), json=config, headers=REQUEST_HEADERS, verify=False)


def get_task_ids():
    tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    return [t['id'] for t in tasks]


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
def get_elasticsearch_index_health(index_name):
    exit_status, output = shakedown.run_command_on_master(
        "{}/_cluster/health/{}'".format(curl_api("GET"), index_name))
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
        "{}/{}/{}/{}?refresh=wait_for' -d '{}'".format(curl_api("PUT"), index_name, index_type, doc_id,
                                                       json.dumps(params)))
    return output


@as_json
def get_document(index_name, index_type, doc_id):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/{}/{}'".format(curl_api("GET"), index_name, index_type, doc_id))
    return output


def uninstall():
    print('Uninstalling/janitoring {}'.format(PACKAGE_NAME))
    try:
        shakedown.uninstall_package_and_wait(PACKAGE_NAME, service_name=PACKAGE_NAME)
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


def curl_api(method):
    return "curl -X{} -s -u elastic:changeme 'http://master.{}.l4lb.thisdcos.directory:9200".format(method, PACKAGE_NAME)


def get_marathon_host():
    return shakedown.get_service_ips('marathon', PACKAGE_NAME).pop()
