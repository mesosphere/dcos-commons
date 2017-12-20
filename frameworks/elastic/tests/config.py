import json
import logging
from functools import wraps

import retrying
import shakedown

import sdk_cmd
import sdk_hosts
import sdk_marathon
import sdk_plan
import sdk_tasks

log = logging.getLogger(__name__)

PACKAGE_NAME = 'beta-elastic'
SERVICE_NAME = 'elastic'

KIBANA_PACKAGE_NAME = 'kibana'

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


def as_json(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            value = fn(*args, **kwargs)
            return json.loads(value)
        except (TypeError, json.JSONDecodeError, ValueError):
            log.info("Failed to parse value returned by \"{}\" as JSON, returning None".format(fn.__name__))
            log.info("Value: {}".format(value))
            return None

    return wrapper


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_KIBANA_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_kibana_adminrouter_integration(path):
    dcos_token = shakedown.dcos_acs_token()
    curl_cmd = "curl -I -k -H \"Authorization: token={}\" -s {}/{}".format(
        dcos_token, shakedown.dcos_url().rstrip('/'), path.lstrip('/'))
    exit_status, output = shakedown.run_command_on_master(curl_cmd)
    return output and "HTTP/1.1 200" in output


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_elasticsearch_index_health(index_name, color, service_name=SERVICE_NAME):
    curl_api = _curl_api(service_name, "GET")
    result = _get_elasticsearch_index_health(curl_api, index_name)
    return result and result["status"] == color


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_custom_elasticsearch_cluster_setting(service_name=SERVICE_NAME):
    curl_api = _curl_api(service_name, "GET")
    expected_setting = 3
    result = _get_elasticsearch_cluster_settings(curl_api)
    setting = result["defaults"]["cluster"]["routing"]["allocation"]["node_initial_primaries_recoveries"]
    log.info('check_custom_elasticsearch_cluster_setting expected {} and got {}'.format(expected_setting, setting))
    return result and expected_setting == int(setting)


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def wait_for_expected_nodes_to_exist(service_name=SERVICE_NAME, task_count=DEFAULT_TASK_COUNT):
    curl_api = _curl_api(service_name, "GET")
    result = _get_elasticsearch_cluster_health(curl_api)
    if result is None:
        return False
    if not "number_of_nodes" in result:
        log.warning("Missing 'number_of_nodes' key in cluster health response: {}".format(result))
        return False
    node_count = result["number_of_nodes"]
    log.info('Waiting for {} healthy nodes, got {}'.format(task_count, node_count))
    return node_count == task_count


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_plugin_installed(plugin_name, service_name=SERVICE_NAME):
    curl_api = _curl_api(service_name, "GET")
    result = _get_hosts_with_plugin(curl_api, plugin_name)
    return result is not None and len(result) == DEFAULT_TASK_COUNT


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=DEFAULT_ELASTIC_TIMEOUT*1000,
    retry_on_result=lambda res: not res)
def check_plugin_uninstalled(plugin_name, service_name=SERVICE_NAME):
    curl_api = _curl_api(service_name, "GET")
    result = _get_hosts_with_plugin(curl_api, plugin_name)
    return result is not None and result == []


def _get_hosts_with_plugin(curl_api, plugin_name):
    exit_status, output = shakedown.run_command_on_master("{}/_cat/plugins'".format(curl_api))
    if exit_status:
        return [host for host in output.split("\n") if plugin_name in host]
    else:
        return None


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=120*1000,
    retry_on_result=lambda res: not res)
def get_elasticsearch_master(service_name=SERVICE_NAME):
    # just in case, re-fetch the _curl_api in case the elasticsearch master is moved:
    exit_status, output = shakedown.run_command_on_master("{}/_cat/master'".format(_curl_api(service_name, "GET")))
    if exit_status and len(output.split()) > 0:
        return output.split()[-1]
    return False


def verify_commercial_api_status(is_enabled, service_name=SERVICE_NAME):
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
        # The graph endpoint doesn't exist without X-Pack installed. In that case Elasticsearch returns a plain text
        # response (non-JSON) which when parsed by our @as_json decorator turns into a None.
        assert response == None


def enable_xpack(service_name=SERVICE_NAME):
    _set_xpack(service_name, "true")


def disable_xpack(service_name=SERVICE_NAME):
    _set_xpack(service_name, "false")


def _set_xpack(service_name, is_enabled):
    # Toggling X-Pack requires full cluster restart, not a rolling restart
    options = {'TASKCFG_ALL_XPACK_ENABLED': is_enabled, 'UPDATE_STRATEGY': 'parallel'}
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
    xpack_license = get_xpack_license(service_name)
    if not "license" in xpack_license:
        log.warning("Missing 'license' key in _xpack/license response: {}".format(xpack_license))
        return False # retry
    assert xpack_license["license"]["status"] == "active"
    return True # done


@as_json
def _get_elasticsearch_index_health(curl_api, index_name):
    exit_status, output = shakedown.run_command_on_master("{}/_cluster/health/{}'".format(curl_api, index_name))
    return output


@as_json
def _get_elasticsearch_cluster_health(curl_api):
    exit_status, output = shakedown.run_command_on_master("{}/_cluster/health'".format(curl_api))
    return output


@as_json
def _get_elasticsearch_cluster_settings(curl_api):
    curl_cluster_settings = "{}/_cluster/settings?include_defaults=true'".format(curl_api)
    exit_status, output = shakedown.run_command_on_master(curl_cluster_settings)
    return output


@as_json
def get_elasticsearch_indices_stats(index_name, service_name=SERVICE_NAME):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/_stats'".format(_curl_api(service_name, "GET"), index_name))
    return output


@as_json
def create_index(index_name, params, service_name=SERVICE_NAME, https=False):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}' -d '{}'".format(
            _curl_api(service_name, "PUT", https=https), index_name, json.dumps(params)))
    return output


@as_json
def graph_api(index_name, query, service_name=SERVICE_NAME):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/_xpack/_graph/_explore' -d '{}'".format(_curl_api(service_name, "POST"), index_name, json.dumps(query)))
    return output


@as_json
def get_xpack_license(service_name=SERVICE_NAME):
    exit_status, output = shakedown.run_command_on_master("{}/_xpack/license'".format(_curl_api(service_name, "GET")))
    return output


@as_json
def delete_index(index_name, service_name=SERVICE_NAME, https=False):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}'".format(_curl_api(service_name, "DELETE", https=https), index_name))
    return output


@as_json
def create_document(index_name, index_type, doc_id, params, service_name=SERVICE_NAME, https=False):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/{}/{}?refresh=wait_for' -d '{}'".format(
            _curl_api(service_name, "PUT", https=https), index_name, index_type, doc_id, json.dumps(params)))
    return output


@as_json
def get_document(index_name, index_type, doc_id, service_name=SERVICE_NAME, https=False):
    exit_status, output = shakedown.run_command_on_master(
        "{}/{}/{}/{}'".format(
            _curl_api(service_name, "GET", https=https), index_name, index_type, doc_id))
    return output


@as_json
def get_elasticsearch_nodes_info(service_name=SERVICE_NAME):
    exit_status, output = shakedown.run_command_on_master("{}/_nodes'".format(_curl_api(service_name, "GET")))
    return output


def _curl_api(service_name, method, role="master", https=False):
    protocol = 'https://' if https else 'http://'
    host = protocol + sdk_hosts.autoip_host(
        service_name, "{}-0-node".format(role), _master_zero_http_port(service_name))
    return ("/opt/mesosphere/bin/curl -X{} -u elastic:changeme -H 'Content-type: application/json' '" + host).format(method)


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
