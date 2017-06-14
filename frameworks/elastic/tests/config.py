import json
from functools import wraps

import shakedown

import sdk_cmd
import sdk_marathon as marathon
import sdk_plan
import sdk_tasks as tasks
import sdk_utils

PACKAGE_NAME = 'elastic'
DEFAULT_TASK_COUNT = 7
WAIT_TIME_IN_SECONDS = 10 * 60
KIBANA_WAIT_TIME_IN_SECONDS = 15 * 60
DEFAULT_INDEX_NAME = 'customer'
DEFAULT_INDEX_TYPE = 'entry'
DCOS_URL = shakedown.run_dcos_command('config show core.dcos_url')[0].strip()
DCOS_TOKEN = shakedown.run_dcos_command('config show core.dcos_acs_token')[0].strip()


def as_json(fn):
    @wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            return json.loads(fn(*args, **kwargs))
        except ValueError:
            return None

    return wrapper


def index_health_success_predicate(index_name, color):
    result = get_elasticsearch_index_health(index_name)
    return result and result["status"] == color


def check_elasticsearch_index_health(index_name, color):
    return shakedown.wait_for(lambda: index_health_success_predicate(index_name, color),
                              timeout_seconds=WAIT_TIME_IN_SECONDS)


def check_kibana_adminrouter_integration(path):
    return shakedown.wait_for(lambda: kibana_health_success_predicate(path),
                              timeout_seconds=KIBANA_WAIT_TIME_IN_SECONDS,
                              noisy=True)


def kibana_health_success_predicate(path):
    result = get_kibana_status(path)
    return result and "HTTP/1.1 200" in result


def get_kibana_status(path):
    token = shakedown.authenticate('bootstrapuser', 'deleteme')
    curl_cmd = "curl -I -k -H \"Authorization: token={}\" -s {}{}".format(
        token, shakedown.dcos_url(), path)
    exit_status, output = shakedown.run_command_on_master(curl_cmd)
    return output


def expected_nodes_success_predicate():
    result = get_elasticsearch_cluster_health()
    if result is None:
        return False
    node_count = result["number_of_nodes"]
    sdk_utils.out('Waiting for {} healthy nodes, got {}'.format(DEFAULT_TASK_COUNT, node_count))
    return node_count == DEFAULT_TASK_COUNT


def wait_for_expected_nodes_to_exist():
    return shakedown.wait_for(lambda: expected_nodes_success_predicate(), timeout_seconds=WAIT_TIME_IN_SECONDS)


def plugins_installed_success_predicate(plugin_name):
    result = get_hosts_with_plugin(plugin_name)
    return result is not None and len(result) == DEFAULT_TASK_COUNT


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
    def get_master():
        exit_status, output = shakedown.run_command_on_master("{}/_cat/master'".format(curl_api("GET", "coordinator")))
        if exit_status and len(output.split()) > 0:
            return output.split()[-1]

        return False

    return shakedown.wait_for(get_master)


def get_hosts_with_plugin(plugin_name):
    exit_status, output = shakedown.run_command_on_master("{}/_cat/plugins'".format(curl_api("GET")))
    if exit_status:
        return [host for host in output.split("\n") if plugin_name in host]
    else:
        return None


def verify_commercial_api_status(is_enabled):
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
    response = graph_api(DEFAULT_INDEX_NAME, query)
    if is_enabled:
        assert response["failures"] == []
    else:
        # The _graph endpoint doesn't even exist without X-Pack installed
        assert response["status"] == 400


def enable_xpack():
    xpack("true")


def disable_xpack():
    xpack("false")


def xpack(is_enabled):
    config = marathon.get_config(PACKAGE_NAME)
    config['env']['TASKCFG_ALL_XPACK_ENABLED'] = is_enabled
    marathon.update_app(PACKAGE_NAME, config)
    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def verify_xpack_license():
    xpack_license = get_xpack_license()
    assert xpack_license["license"]["status"] == "active"


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
def get_xpack_license():
    command = "{}/_xpack/license'".format(curl_api("GET"))
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
    vip = "http://{}-0-node.{}.autoip.dcos.thisdcos.directory:{}".format(role, PACKAGE_NAME, master_zero_http_port())
    return ("curl -X{} -s -u elastic:changeme '" + vip).format(method)


def master_zero_http_port():
    ret_str = sdk_cmd.run_cli('{} endpoints master'.format(PACKAGE_NAME))
    result = json.loads(ret_str)
    dns = result['dns']
    # array will initially look something like this in CCM, with some 9300 ports and some lower ones [
    #   "master-0-node.elastic.autoip.dcos.thisdcos.directory:9300",
    #   "master-0-node.elastic.autoip.dcos.thisdcos.directory:1025",
    #   "master-1-node.elastic.autoip.dcos.thisdcos.directory:9300",
    #   "master-1-node.elastic.autoip.dcos.thisdcos.directory:1025",
    #   "master-2-node.elastic.autoip.dcos.thisdcos.directory:9300",
    #   "master-2-node.elastic.autoip.dcos.thisdcos.directory:1025"
    # ]

    # sort will bubble up "master-0-node.elastic.autoip.dcos.thisdcos.directory:1025", the HTTP server host:port
    dns.sort()
    port = dns[0].split(':')[-1]
    sdk_utils.out("Extracted {} as port for {}".format(port, dns[0]))
    return port
