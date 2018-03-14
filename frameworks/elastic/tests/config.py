import json
import logging

import retrying
import shakedown

import sdk_cmd
import sdk_hosts
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils

log = logging.getLogger(__name__)

PACKAGE_NAME = 'elastic'
SERVICE_NAME = 'elastic'

KIBANA_PACKAGE_NAME = 'kibana'

XPACK_PLUGIN_NAME = 'x-pack'

# sum of default pod counts, with one task each:
# - master: 3
# - data: 2
# - ingest: 0
# - coordinator: 1
DEFAULT_TASK_COUNT = 6
# TODO: add and use throughout a method to determine expected task count based on options .
#       the method should provide for use cases:
#         * total count, ie 6
#         * count for a specific type, ie 3
#         * count by type, ie [{'ingest':1},{'data':3},...]

DEFAULT_ELASTIC_TIMEOUT = 30 * 60
DEFAULT_KIBANA_TIMEOUT = 30 * 60
DEFAULT_INDEX_NAME = 'customer'
DEFAULT_INDEX_TYPE = 'entry'

ENDPOINT_TYPES = (
    'coordinator-http', 'coordinator-transport',
    'data-http', 'data-transport',
    'master-http', 'master-transport')
# TODO: similar to DEFAULT_TASK_COUNT, whether or not ingest-http is present is dependent upon
# options.
#    'ingest-http', 'ingest-transport',

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


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_KIBANA_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_kibana_adminrouter_integration(path):
    curl_cmd = "curl -I -k -H \"Authorization: token={}\" -s {}/{}".format(
        shakedown.dcos_acs_token(), shakedown.dcos_url().rstrip('/'), path.lstrip('/'))
    exit_ok, output = shakedown.run_command_on_master(curl_cmd)
    return exit_ok and output and "HTTP/1.1 200" in output


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_elasticsearch_index_health(index_name, color, service_name=SERVICE_NAME):
    result = _curl_query(service_name, "GET", "_cluster/health/{}".format(index_name))
    return result and result["status"] == color


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_custom_elasticsearch_cluster_setting(service_name=SERVICE_NAME):
    result = _curl_query(service_name, "GET", "_cluster/settings?include_defaults=true")
    if not result:
        return False
    expected_setting = 3
    setting = result["defaults"]["cluster"]["routing"]["allocation"]["node_initial_primaries_recoveries"]
    log.info('check_custom_elasticsearch_cluster_setting expected {} and got {}'.format(expected_setting, setting))
    return expected_setting == int(setting)


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def wait_for_expected_nodes_to_exist(service_name=SERVICE_NAME, task_count=DEFAULT_TASK_COUNT):
    result = _curl_query(service_name, "GET", "_cluster/health")
    if not result or not "number_of_nodes" in result:
        log.warning("Missing 'number_of_nodes' key in cluster health response: {}".format(result))
        return False
    node_count = result["number_of_nodes"]
    log.info('Waiting for {} healthy nodes, got {}'.format(task_count, node_count))
    return node_count == task_count


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_kibana_plugin_installed(plugin_name, service_name=SERVICE_NAME):
    cmd = "KIBANA_DIRECTORY=$(ls -d $MESOS_SANDBOX/kibana-*-linux-x86_64); $KIBANA_DIRECTORY/bin/kibana-plugin list"
    _, stdout, _ = sdk_cmd.task_exec(service_name, cmd)
    return plugin_name in stdout


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_elasticsearch_plugin_installed(plugin_name, service_name=SERVICE_NAME):
    result = _get_hosts_with_plugin(service_name, plugin_name)
    return result is not None and len(result) == DEFAULT_TASK_COUNT


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_elasticsearch_plugin_uninstalled(plugin_name, service_name=SERVICE_NAME):
    result = _get_hosts_with_plugin(service_name, plugin_name)
    return result is not None and result == []


def _get_hosts_with_plugin(service_name, plugin_name):
    output = _curl_query(service_name, "GET", "_cat/plugins", return_json=False)
    if output is None:
        return None
    return [host for host in output.split("\n") if plugin_name in host]


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=120*1000,
    retry_on_result=lambda res: not res)
def get_elasticsearch_master(service_name=SERVICE_NAME):
    output = _curl_query(service_name, "GET", "_cat/master", return_json=False)
    if output is not None and len(output.split()) > 0:
        return output.split()[-1]
    return False


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=120*1000,
    retry_on_result=lambda res: not res)
def verify_commercial_api_status(is_enabled, service_name=SERVICE_NAME):
    query = {
        "query": { "match": { "name": "*" } },
        "vertices": [ { "field": "name" } ],
        "connections": { "vertices": [ { "field": "role" } ] }
    }

    # The graph endpoint doesn't exist without X-Pack installed.
    # In that case Elasticsearch returns a plain text error:
    # "No handler found for uri [/INDEX_NAME/_xpack/graph/explore] and method [POST]"
    response = _curl_query(
        service_name, "POST",
        "{}/_xpack/_graph/_explore".format(DEFAULT_INDEX_NAME),
        json_data=query,
        return_json=is_enabled)
    if is_enabled:
        return is_graph_endpoint_active(response)
    else:
        return "No handler found" in response


# The graph endpoint response looks something like:
# {
#   "took": 200,
#   "timed_out": false,
#   "failures": [],
#   "vertices": [],
#   "connections": []
# }
def is_graph_endpoint_active(response):
    return isinstance(response["vertices"], list) and isinstance(response["connections"], list)


def set_xpack(is_enabled, service_name=SERVICE_NAME):
    # Toggling X-Pack requires full cluster restart, not a rolling restart
    options = {'TASKCFG_ALL_XPACK_ENABLED': str(is_enabled).lower(), 'UPDATE_STRATEGY': 'parallel'}
    update_app(service_name, options, DEFAULT_TASK_COUNT)


def update_app(service_name, options, expected_task_count):
    config = sdk_marathon.get_config(service_name)
    config['env'].update(options)
    sdk_marathon.update_app(service_name, config)
    sdk_plan.wait_for_completed_deployment(service_name)
    sdk_tasks.check_running(service_name, expected_task_count)


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=120*1000,
    retry_on_result=lambda res: not res)
def verify_xpack_license(service_name=SERVICE_NAME):
    xpack_license = _curl_query(service_name, "GET", '_xpack/license')
    if not "license" in xpack_license:
        log.warning("Missing 'license' key in _xpack/license response: {}".format(xpack_license))
        return False # retry
    assert xpack_license["license"]["status"] == "active"
    return True # done


def get_elasticsearch_indices_stats(index_name, service_name=SERVICE_NAME):
    return _curl_query(service_name, "GET", "{}/_stats".format(index_name))


def create_index(index_name, params, service_name=SERVICE_NAME, https=False):
    return _curl_query(service_name, "PUT", index_name, json_data=params, https=https)


def delete_index(index_name, service_name=SERVICE_NAME, https=False):
    return _curl_query(service_name, "DELETE", index_name, https=https)


def create_document(index_name, index_type, doc_id, params, service_name=SERVICE_NAME, https=False):
    return _curl_query(
        service_name, "PUT",
        "{}/{}/{}?refresh=wait_for".format(index_name, index_type, doc_id),
        json_data=params,
        https=https)


def get_document(index_name, index_type, doc_id, service_name=SERVICE_NAME, https=False):
    return _curl_query(
        service_name, "GET", "{}/{}/{}".format(index_name, index_type, doc_id), https=https)


def get_elasticsearch_nodes_info(service_name=SERVICE_NAME):
    return _curl_query(service_name, "GET", "_nodes")


# Here we only retry if the command itself failed, or if the data couldn't be parsed as JSON when return_json=True.
# Upstream callers may want to have their own retry loop against the content of the returned data (e.g. expected field is missing).
@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=120*1000,
    retry_on_result=lambda res: res is None)
def _curl_query(service_name, method, endpoint, json_data=None, role="master", https=False, return_json=True):
    protocol = 'https' if https else 'http'
    host = sdk_hosts.autoip_host(service_name, "{}-0-node".format(role), _master_zero_http_port(service_name))
    curl_cmd = "/opt/mesosphere/bin/curl -sS -u elastic:changeme -X{} '{}://{}/{}'".format(method, protocol, host, endpoint)
    if json_data:
        curl_cmd += " -H 'Content-type: application/json' -d '{}'".format(json.dumps(json_data))
    task_name = "master-0-node"
    exit_code, stdout, stderr = sdk_cmd.task_exec(task_name, curl_cmd)

    def build_errmsg(msg):
        return "{}\nCommand:\n{}\nstdout:\n{}\nstderr:\n{}".format(msg, curl_cmd, stdout, stderr)

    if exit_code:
        log.warning(build_errmsg("Failed to run command on {}, retrying or giving up.".format(task_name)))
        return None

    if not return_json:
        return stdout

    try:
        return json.loads(stdout)
    except:
        log.warning(build_errmsg("Failed to parse stdout as JSON, retrying or giving up."))
        return None


def _master_zero_http_port(service_name):
    dns = sdk_cmd.svc_cli(PACKAGE_NAME, service_name, 'endpoints master-http', json=True, print_output=False)['dns']
    # 'dns' array will look something like this in CCM: [
    #   "master-0-node.elastic.[...]:1025",
    #   "master-1-node.elastic.[...]:1025",
    #   "master-2-node.elastic.[...]:1025"
    # ]

    port = dns[0].split(':')[-1]
    log.info("Extracted {} as port for {}".format(port, dns[0]))
    return port
