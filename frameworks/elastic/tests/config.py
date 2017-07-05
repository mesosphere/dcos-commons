import json
from functools import wraps

import shakedown

import sdk_cmd
import sdk_hosts as hosts
import sdk_marathon as marathon
import sdk_plan
import sdk_tasks as tasks
import sdk_utils

PACKAGE_NAME = 'elastic'

DEFAULT_TASK_COUNT = 7
WAIT_TIME_IN_SECONDS = 10 * 60
KIBANA_WAIT_TIME_IN_SECONDS = 30 * 60
DEFAULT_INDEX_NAME = 'customer'
DEFAULT_INDEX_TYPE = 'entry'

DCOS_TOKEN = shakedown.run_dcos_command('config show core.dcos_acs_token')[0].strip()


DEFAULT_NUMBER_OF_SHARDS = 1
DEFAULT_NUMBER_OF_REPLICAS = 1
DEFAULT_SETTINGS_MAPPINGS = {
    "settings": {
        "index.unassigned.node_left.delayed_timeout": "0",
        "number_of_shards": DEFAULT_NUMBER_OF_SHARDS,
        "number_of_replicas": DEFAULT_NUMBER_OF_REPLICAS},
    "mappings": {
        DEFAULT_INDEX_TYPE: {
            "properties": {
                "name": {"type": "keyword"},
                "role": {"type": "keyword"}}}}}


def as_json(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            return json.loads(fn(*args, **kwargs))
        except ValueError:
            return None

    return wrapper


def check_kibana_adminrouter_integration(path):
    curl_cmd = "curl -I -k -H \"Authorization: token={}\" -s {}/{}".format(
        DCOS_TOKEN, shakedown.dcos_url().rstrip('/'), path.lstrip('/'))
    def fun():
        exit_status, output = shakedown.run_command_on_master(curl_cmd)
        return output and "HTTP/1.1 200" in output

    return shakedown.wait_for(fun, timeout_seconds=KIBANA_WAIT_TIME_IN_SECONDS, noisy=True)


def check_elasticsearch_index_health(index_name, color, service_name=PACKAGE_NAME):
    curl_api = _curl_api(service_name, "GET")
    def fun():
        result = _get_elasticsearch_index_health(curl_api, index_name)
        return result and result["status"] == color

    return shakedown.wait_for(fun, timeout_seconds=WAIT_TIME_IN_SECONDS)


def wait_for_expected_nodes_to_exist(service_name=PACKAGE_NAME):
    curl_api = _curl_api(service_name, "GET")
    def expected_nodes():
        result = _get_elasticsearch_cluster_health(curl_api)
        if result is None:
            return False
        node_count = result["number_of_nodes"]
        sdk_utils.out('Waiting for {} healthy nodes, got {}'.format(DEFAULT_TASK_COUNT, node_count))
        return node_count == DEFAULT_TASK_COUNT

    return shakedown.wait_for(expected_nodes, timeout_seconds=WAIT_TIME_IN_SECONDS)


def check_plugin_installed(plugin_name, service_name=PACKAGE_NAME):
    curl_api = _curl_api(service_name, "GET")
    def fun():
        result = _get_hosts_with_plugin(curl_api, plugin_name)
        return result is not None and len(result) == DEFAULT_TASK_COUNT

    return shakedown.wait_for(fun, timeout_seconds=WAIT_TIME_IN_SECONDS)


def check_plugin_uninstalled(plugin_name, service_name=PACKAGE_NAME):
    curl_api = _curl_api(service_name, "GET")
    def fun():
        result = _get_hosts_with_plugin(curl_api, plugin_name)
        return result is not None and result == []

    return shakedown.wait_for(fun, timeout_seconds=WAIT_TIME_IN_SECONDS)


def _get_hosts_with_plugin(curl_api, plugin_name):
    exit_status, output = shakedown.run_command_on_master("{}/_cat/plugins'".format(curl_api))
    if exit_status:
        return [host for host in output.split("\n") if plugin_name in host]
    else:
        return None


def get_elasticsearch_master(service_name=PACKAGE_NAME):
    # just in case, re-fetch the _curl_api in case the elasticsearch master is moved:
    def get_master():
        exit_status, output = shakedown.run_command_on_master(
            "{}/_cat/master'".format(_curl_api(service_name, "GET")))
        if exit_status and len(output.split()) > 0:
            return output.split()[-1]

        return False

    return shakedown.wait_for(get_master)


def verify_commercial_api_status(is_enabled, service_name=PACKAGE_NAME):
    query = {
        "query": {
            "match": {
                "name": "*"
            }
        },
        "vertices": [
            {
                "field": "name"
            }
        ],
        "connections": {
            "vertices": [
                {
                    "field": "role"
                }
            ]
        }
    }
    response = graph_api(DEFAULT_INDEX_NAME, query, service_name=service_name)
    if is_enabled:
        assert response["failures"] == []
    else:
        # The _graph endpoint doesn't even exist without X-Pack installed
        assert response["status"] == 400


def enable_xpack(service_name=PACKAGE_NAME):
    _set_xpack(service_name, "true")


def disable_xpack(service_name=PACKAGE_NAME):
    _set_xpack(service_name, "false")


def _set_xpack(service_name, is_enabled):
    config = marathon.get_config(service_name)
    config['env']['TASKCFG_ALL_XPACK_ENABLED'] = is_enabled
    marathon.update_app(service_name, config)
    sdk_plan.wait_for_completed_deployment(service_name)
    tasks.check_running(service_name, DEFAULT_TASK_COUNT)


def verify_xpack_license(service_name=PACKAGE_NAME):
    xpack_license = get_xpack_license(service_name)
    assert xpack_license["license"]["status"] == "active"


@as_json
def _get_elasticsearch_index_health(curl_api, index_name):
    exit_status, output = shakedown.run_command_on_master("{}/_cluster/health/{}'".format(curl_api, index_name))
    return output


@as_json
def _get_elasticsearch_cluster_health(curl_api):
    exit_status, output = shakedown.run_command_on_master("{}/_cluster/health'".format(curl_api))
    return output


@as_json
def get_elasticsearch_indices_stats(index_name, service_name=PACKAGE_NAME):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/_stats'".format(_curl_api(service_name, "GET"), index_name))
    return output


@as_json
def create_index(index_name, params, service_name=PACKAGE_NAME):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}' -d '{}'".format(_curl_api(service_name, "PUT"), index_name, json.dumps(params)))
    return output


@as_json
def graph_api(index_name, query, service_name=PACKAGE_NAME):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/_graph/explore' -d '{}'".format(_curl_api(service_name, "POST"), index_name, json.dumps(query)))
    return output


@as_json
def get_xpack_license(service_name=PACKAGE_NAME):
    exit_status, output = shakedown.run_command_on_master(
        "{}/_xpack/license'".format(_curl_api(service_name, "GET")))
    return output


@as_json
def delete_index(index_name, service_name=PACKAGE_NAME):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}'".format(_curl_api(service_name, "DELETE"), index_name))
    return output


@as_json
def create_document(index_name, index_type, doc_id, params, service_name=PACKAGE_NAME):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/{}/{}?refresh=wait_for' -d '{}'".format(
            _curl_api(service_name, "PUT"), index_name, index_type, doc_id, json.dumps(params)))
    return output


@as_json
def get_document(index_name, index_type, doc_id, service_name=PACKAGE_NAME):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/{}/{}'".format(_curl_api(service_name, "GET"), index_name, index_type, doc_id))
    return output


def _curl_api(service_name, method, role="master"):
    host = "http://" + hosts.autoip_host(service_name, "{}-0-node".format(role), _master_zero_http_port(service_name))
    return ("curl -X{} -s -u elastic:changeme '" + host).format(method)


def _master_zero_http_port(service_name):
    dns = json.loads(sdk_cmd.run_cli(
        '{} --name={} endpoints master'.format(PACKAGE_NAME, service_name),
        print_output=False))['dns']
    # array will initially look something like this in CCM, with some 9300 ports and some lower ones [
    #   "master-0-node.elastic.[...]:9300",
    #   "master-0-node.elastic.[...]:1025",
    #   "master-1-node.elastic.[...]:9300",
    #   "master-1-node.elastic.[...]:1025",
    #   "master-2-node.elastic.[...]:9300",
    #   "master-2-node.elastic.[...]:1025"
    # ]

    # sort will bubble up "master-0-node.elastic.[...]:1025", the HTTP server host:port
    dns.sort()
    port = dns[0].split(':')[-1]
    sdk_utils.out("Extracted {} as port for {}".format(port, dns[0]))
    return port
