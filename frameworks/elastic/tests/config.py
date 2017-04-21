import json
from functools import wraps

import shakedown
import sdk_utils

PACKAGE_NAME = 'elastic'
DEFAULT_TASK_COUNT = 9
WAIT_TIME_IN_SECONDS = 6 * 60
KIBANA_WAIT_TIME_IN_SECONDS = 15 * 60
DEFAULT_NODE_COUNT = 7
DEFAULT_INDEX_NAME = 'customer'
DEFAULT_INDEX_TYPE = 'entry'
DCOS_URL = shakedown.run_dcos_command('config show core.dcos_url')[0].strip()
DCOS_TOKEN = shakedown.run_dcos_command('config show core.dcos_acs_token')[0].strip()

TASK_RUNNING_STATE = 'TASK_RUNNING'


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


def index_health_success_predicate(index_name, color):
    result = get_elasticsearch_index_health(index_name)
    return result and result["status"] == color


def check_elasticsearch_index_health(index_name, color):
    return shakedown.wait_for(lambda: index_health_success_predicate(index_name, color),
                              timeout_seconds=WAIT_TIME_IN_SECONDS)


def kibana_health_success_predicate():
    result = get_kibana_status()
    return result and "kbn-name: kibana" in result


def check_kibana_proxylite_adminrouter_integration():
    return shakedown.wait_for(lambda: kibana_health_success_predicate(),
                              timeout_seconds=KIBANA_WAIT_TIME_IN_SECONDS)


def get_kibana_status():
    token = shakedown.authenticate('bootstrapuser', 'deleteme')
    curl_cmd = "curl -I -k -H \"Authorization: token={}\" -s {}/kibana/login".format(
        token, shakedown.dcos_service_url(PACKAGE_NAME))
    exit_status, output = shakedown.run_command_on_master(curl_cmd)
    return output


def expected_nodes_success_predicate():
    result = get_elasticsearch_cluster_health()
    if result is None:
        return False
    node_count = result["number_of_nodes"]
    sdk_utils.out('Waiting for {} healthy nodes, got {}'.format(DEFAULT_NODE_COUNT, node_count))
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


def get_elasticsearch_master():
    exit_status, output = shakedown.run_command_on_master("{}/_cat/master'".format(curl_api("GET", "coordinator")))
    return output.split()[-1]


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


def curl_api(method, role="master"):
    vip = "http://{}.{}.l4lb.thisdcos.directory:9200".format(role, PACKAGE_NAME)
    return ("curl -X{} -s -u elastic:changeme '" + vip).format(method)
