import json
import logging
import re
import retrying

import sdk_cmd
import sdk_hosts
import sdk_marathon
import sdk_networks
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils

log = logging.getLogger(__name__)

PACKAGE_NAME = "elastic"
SERVICE_NAME = "elastic"

DEFAULT_ELASTICSEARCH_USER = "elastic"
DEFAULT_ELASTICSEARCH_PASSWORD = "changeme"
DEFAULT_KIBANA_USER = "kibana"
DEFAULT_KIBANA_PASSWORD = "changeme"

KIBANA_PACKAGE_NAME = "kibana"
KIBANA_SERVICE_NAME = "kibana"
KIBANA_DEFAULT_TIMEOUT = 5 * 60

# sum of default pod counts, with one task each:
# - master: 3
# - data: 2
# - ingest: 0
# - coordinator: 1
DEFAULT_TASK_COUNT = 6
# TODO: add and use throughout a method to determine expected task count based on options.
#       the method should provide for use cases:
#         * total count, ie 6
#         * count for a specific type, ie 3
#         * count by type, ie [{'ingest':1},{'data':3},...]

DEFAULT_TIMEOUT = 30 * 60
DEFAULT_INDEX_NAME = "customer"
DEFAULT_INDEX_TYPE = "entry"

ENDPOINT_TYPES = (
    "coordinator-http",
    "coordinator-transport",
    "data-http",
    "data-transport",
    "master-http",
    "master-transport",
)
# TODO: similar to DEFAULT_TASK_COUNT, whether or not ingest-http is present is dependent upon
# options.
#    'ingest-http', 'ingest-transport',

DEFAULT_NUMBER_OF_SHARDS = 1
DEFAULT_NUMBER_OF_REPLICAS = 1
DEFAULT_SETTINGS_MAPPINGS = {
    "settings": {
        "index.unassigned.node_left.delayed_timeout": "0",
        "number_of_shards": DEFAULT_NUMBER_OF_SHARDS,
        "number_of_replicas": DEFAULT_NUMBER_OF_REPLICAS,
    },
    "mappings": {
        DEFAULT_INDEX_TYPE: {
            "properties": {"name": {"type": "keyword"}, "role": {"type": "keyword"}}
        }
    },
}


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=KIBANA_DEFAULT_TIMEOUT * 1000,
    retry_on_result=lambda res: not res,
)
def check_kibana_adminrouter_integration(path):
    curl_cmd = 'curl -L -I -k -H "Authorization: token={}" -s {}/{}'.format(
        sdk_utils.dcos_token(), sdk_utils.dcos_url().rstrip("/"), path.lstrip("/")
    )
    rc, stdout, _ = sdk_cmd.master_ssh(curl_cmd)
    return rc == 0 and stdout and "HTTP/1.1 200" in stdout


@retrying.retry(
    wait_fixed=1000, stop_max_delay=DEFAULT_TIMEOUT * 1000, retry_on_result=lambda res: not res
)
def check_elasticsearch_index_health(
    index_name, color, service_name=SERVICE_NAME, http_user=None, http_password=None
):
    result = _curl_query(
        service_name,
        "GET",
        "_cluster/health/{}?pretty".format(index_name),
        http_user=http_user,
        http_password=http_password,
    )
    return result and result["status"] == color


@retrying.retry(
    wait_fixed=1000, stop_max_delay=DEFAULT_TIMEOUT * 1000, retry_on_result=lambda res: not res
)
def check_custom_elasticsearch_cluster_setting(service_name=SERVICE_NAME):
    result = _curl_query(service_name, "GET", "_cluster/settings?include_defaults=true")
    if not result:
        return False
    expected_setting = 3
    setting = result["defaults"]["cluster"]["routing"]["allocation"][
        "node_initial_primaries_recoveries"
    ]
    log.info(
        "check_custom_elasticsearch_cluster_setting expected {} and got {}".format(
            expected_setting, setting
        )
    )
    return expected_setting == int(setting)


@retrying.retry(
    wait_fixed=1000, stop_max_delay=DEFAULT_TIMEOUT * 1000, retry_on_result=lambda res: not res
)
def wait_for_expected_nodes_to_exist(service_name=SERVICE_NAME, task_count=DEFAULT_TASK_COUNT):
    result = _curl_query(service_name, "GET", "_cluster/health")
    if not result or "number_of_nodes" not in result:
        log.warning("Missing 'number_of_nodes' key in cluster health response: {}".format(result))
        return False
    node_count = result["number_of_nodes"]
    log.info("Waiting for {} healthy nodes, got {}".format(task_count, node_count))
    return node_count == task_count


@retrying.retry(
    wait_fixed=1000, stop_max_delay=DEFAULT_TIMEOUT * 1000, retry_on_result=lambda res: not res
)
def check_kibana_plugin_installed(plugin_name, service_name=SERVICE_NAME):
    task_sandbox = sdk_cmd.get_task_sandbox_path(service_name)
    # Environment variables aren't available on DC/OS 1.9 so we manually inject MESOS_SANDBOX (and
    # can't use ELASTIC_VERSION).
    #
    # TODO(mpereira): improve this by making task environment variables available in task_exec
    # commands on 1.9.
    #
    # Ticket: https://jira.mesosphere.com/browse/INFINITY-3360
    cmd = "bash -c 'KIBANA_DIRECTORY=$(ls -d {}/kibana-*-linux-x86_64); $KIBANA_DIRECTORY/bin/kibana-plugin list'".format(
        task_sandbox
    )
    _, stdout, _ = sdk_cmd.marathon_task_exec(service_name, cmd)
    return plugin_name in stdout


@retrying.retry(
    wait_fixed=1000, stop_max_delay=DEFAULT_TIMEOUT * 1000, retry_on_result=lambda res: not res
)
def check_elasticsearch_plugin_installed(plugin_name, service_name=SERVICE_NAME):
    result = _get_hosts_with_plugin(service_name, plugin_name)
    return result is not None and len(result) == DEFAULT_TASK_COUNT


@retrying.retry(
    wait_fixed=1000, stop_max_delay=DEFAULT_TIMEOUT * 1000, retry_on_result=lambda res: not res
)
def check_elasticsearch_plugin_uninstalled(plugin_name, service_name=SERVICE_NAME):
    result = _get_hosts_with_plugin(service_name, plugin_name)
    return result is not None and result == []


def _get_hosts_with_plugin(service_name, plugin_name):
    output = _curl_query(service_name, "GET", "_cat/plugins", return_json=False)
    if output is None:
        return None
    return [host for host in output.split("\n") if plugin_name in host]


@retrying.retry(wait_fixed=1000, stop_max_delay=120 * 1000, retry_on_result=lambda res: not res)
def get_elasticsearch_master(service_name=SERVICE_NAME):
    output = _curl_query(service_name, "GET", "_cat/master", return_json=False)
    if output is not None and len(output.split()) > 0:
        return output.split()[-1]
    return False


@retrying.retry(wait_fixed=1000, stop_max_delay=30 * 1000, retry_on_result=lambda res: not res)
def verify_graph_explore_endpoint(
    is_expected_to_be_enabled, service_name=SERVICE_NAME, http_user=None, http_password=None
):
    index_name = "graph_index"

    create_index(
        index_name,
        DEFAULT_SETTINGS_MAPPINGS,
        service_name=service_name,
        http_user=http_user,
        http_password=http_password,
    )

    query = {
        "query": {"match": {"name": "*"}},
        "vertices": [{"field": "name"}],
        "connections": {"vertices": [{"field": "role"}]},
    }

    response = explore_graph(
        service_name, index_name, query, http_user=http_user, http_password=http_password
    )

    delete_index(
        index_name, service_name=service_name, http_user=http_user, http_password=http_password
    )

    return is_expected_to_be_enabled == is_graph_explore_endpoint_active(response)


def verify_commercial_api_status(
    is_expected_to_be_enabled, service_name=SERVICE_NAME, http_user=None, http_password=None
):
    return verify_graph_explore_endpoint(
        is_expected_to_be_enabled, service_name, http_user=http_user, http_password=http_password
    )


# On Elastic 6.x, the "Graph Explore API" is available when the Elasticsearch cluster is configured
# with a "trial" X-Pack license. When configured with a "basic" license (the default) the API will
# respond with an HTTP 403.
#
# A "Graph Explore API" response will look something like:
#   1. With a "trial" license:
#   {
#     "took": 183,
#     "timed_out": false,
#     "failures": [],
#     "vertices": [...],
#     "connections": [...]
#   }
#
#   2. With a "basic" license:
#   {
#     "error": {
#       "root_cause": [
#         {
#           "type": "security_exception",
#           "reason": "current license is non-compliant for [graph]",
#           "license.expired.feature": "graph"
#         }
#       ],
#       "type": "security_exception",
#       "reason": "current license is non-compliant for [graph]",
#       "license.expired.feature": "graph"
#     },
#     "status": 403
#   }
def is_graph_explore_endpoint_active(response):
    return isinstance(response.get("vertices"), list) and isinstance(
        response.get("connections"), list
    )


def verify_document(service_name, document_id, document_fields, http_user=None, http_password=None):
    document = config.get_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        document_id,
        service_name=service_name,
        http_user=http_user,
        http_password=http_password,
    )
    assert document["_source"]["name"] == document_fields["name"]


def update_app(service_name, options, expected_task_count):
    config = sdk_marathon.get_config(service_name)
    config["env"].update(options)
    sdk_marathon.update_app(config)
    sdk_plan.wait_for_completed_deployment(service_name)
    sdk_tasks.check_running(service_name, expected_task_count)


def get_xpack_license(service_name=SERVICE_NAME, http_user=None, http_password=None):
    return _curl_query(
        service_name, "GET", "_xpack/license", http_user=http_user, http_password=http_password
    )


@retrying.retry(wait_fixed=1000, stop_max_delay=120 * 1000, retry_on_result=lambda res: not res)
def verify_xpack_license(
    license_type, service_name=SERVICE_NAME, http_user=None, http_password=None
):
    response = get_xpack_license(service_name, http_user=http_user, http_password=http_password)

    if "license" not in response:
        log.warning("Missing 'license' key in _xpack/license response: {}".format(response))
        return False  # retry

    assert response["license"]["status"] == "active"
    assert response["license"]["type"] == license_type

    return True  # done


@retrying.retry(
    wait_fixed=1000, stop_max_delay=5 * 1000, retry_on_result=lambda return_value: not return_value
)
def setup_passwords(service_name=SERVICE_NAME, task_name="master-0-node"):
    cmd = "\n".join(
        [
            "set -x",
            "export JAVA_HOME=$(ls -d ${MESOS_SANDBOX}/jdk*/jre/)",
            "ELASTICSEARCH_PATH=$(ls -d ${MESOS_SANDBOX}/elasticsearch-*/)",
            "${ELASTICSEARCH_PATH}/bin/elasticsearch-setup-passwords auto --batch --verbose",
        ]
    )
    full_cmd = "bash -c '{}'".format(cmd)
    _, stdout, _ = sdk_cmd.service_task_exec(service_name, task_name, full_cmd)

    elastic_password = re.search("PASSWORD elastic = (.*)", stdout).group(1)
    kibana_password = re.search("PASSWORD kibana = (.*)", stdout).group(1)

    if not elastic_password or not kibana_password:
        # Retry.
        return False

    return {"elastic": elastic_password, "kibana": kibana_password}


def explore_graph(
    service_name=SERVICE_NAME,
    index_name=DEFAULT_INDEX_NAME,
    query={},
    http_user=None,
    http_password=None,
):
    return _curl_query(
        service_name,
        "POST",
        "{}/_xpack/_graph/_explore".format(index_name),
        json_body=query,
        http_user=http_user,
        http_password=http_password,
    )


def start_trial_license(service_name=SERVICE_NAME):
    return _curl_query(service_name, "POST", "_xpack/license/start_trial?acknowledge=true")


def get_elasticsearch_indices_stats(index_name, service_name=SERVICE_NAME):
    return _curl_query(service_name, "GET", "{}/_stats".format(index_name))


def create_index(
    index_name, params, service_name=SERVICE_NAME, https=False, http_user=None, http_password=None
):
    return _curl_query(
        service_name,
        "PUT",
        index_name,
        json_body=params,
        https=https,
        http_user=http_user,
        http_password=http_password,
    )


def delete_index(
    index_name, service_name=SERVICE_NAME, https=False, http_user=None, http_password=None
):
    return _curl_query(
        service_name,
        "DELETE",
        index_name,
        https=https,
        http_user=http_user,
        http_password=http_password,
    )


def create_document(
    index_name,
    index_type,
    doc_id,
    params,
    service_name=SERVICE_NAME,
    https=False,
    http_user=None,
    http_password=None,
):
    return _curl_query(
        service_name,
        "PUT",
        "{}/{}/{}?refresh=wait_for".format(index_name, index_type, doc_id),
        json_body=params,
        https=https,
        http_user=http_user,
        http_password=http_password,
    )


def get_document(
    index_name,
    index_type,
    doc_id,
    service_name=SERVICE_NAME,
    https=False,
    http_user=None,
    http_password=None,
):
    return _curl_query(
        service_name,
        "GET",
        "{}/{}/{}".format(index_name, index_type, doc_id),
        https=https,
        http_user=http_user,
        http_password=http_password,
    )


def get_elasticsearch_nodes_info(service_name=SERVICE_NAME):
    return _curl_query(service_name, "GET", "_nodes")


# Here we only retry if the command itself failed, or if the data couldn't be parsed as JSON when
# return_json=True. Upstream callers may want to have their own retry loop against the content of
# the returned data (e.g. expected field is missing).
@retrying.retry(wait_fixed=1000, stop_max_delay=120 * 1000, retry_on_result=lambda res: res is None)
def _curl_query(
    service_name,
    method,
    endpoint,
    json_body=None,
    task="master-0-node",
    https=False,
    return_json=True,
    http_user=DEFAULT_ELASTICSEARCH_USER,
    http_password=DEFAULT_ELASTICSEARCH_PASSWORD,
):
    protocol = "https" if https else "http"

    if http_password and not http_user:
        raise Exception(
            "HTTP authentication won't work with just a password. Needs at least user, or both user AND password"
        )

    credentials = ""
    if http_user:
        credentials = "-u {}".format(http_user)
    if http_password:
        credentials = "{}:{}".format(credentials, http_password)

    host = sdk_hosts.autoip_host(service_name, task, _master_zero_http_port(service_name))

    curl_cmd = "/opt/mesosphere/bin/curl -sS {} -X{} '{}://{}/{}'".format(
        credentials, method, protocol, host, endpoint
    )

    if json_body:
        curl_cmd += " -H 'Content-type: application/json' -d '{}'".format(json.dumps(json_body))

    task_name = "master-0-node"
    exit_code, stdout, stderr = sdk_cmd.service_task_exec(service_name, task_name, curl_cmd)

    def build_errmsg(msg):
        return "{}\nCommand:\n{}\nstdout:\n{}\nstderr:\n{}".format(msg, curl_cmd, stdout, stderr)

    if exit_code:
        log.warning(
            build_errmsg("Failed to run command on {}, retrying or giving up.".format(task_name))
        )
        return None

    if not return_json:
        return stdout

    try:
        return json.loads(stdout)
    except Exception:
        log.warning(build_errmsg("Failed to parse stdout as JSON, retrying or giving up."))
        return None


# TODO(mpereira): it is safe to remove this test after the 6.x release.
def test_xpack_enabled_update(service_name, from_xpack_enabled, to_xpack_enabled):
    sdk_upgrade.test_upgrade(
        PACKAGE_NAME,
        service_name,
        DEFAULT_TASK_COUNT,
        additional_options={"elasticsearch": {"xpack_enabled": from_xpack_enabled}},
        test_version_additional_options={
            "service": {"update_strategy": "parallel"},
            "elasticsearch": {"xpack_enabled": to_xpack_enabled},
        },
    )

    wait_for_expected_nodes_to_exist(service_name=service_name, task_count=DEFAULT_TASK_COUNT)


# TODO(mpereira): change this to xpack_security_enabled to xpack_security_enabled after the 6.x
# release.
def test_update_from_xpack_enabled_to_xpack_security_enabled(
    service_name, xpack_enabled, xpack_security_enabled
):
    sdk_upgrade.test_upgrade(
        PACKAGE_NAME,
        service_name,
        DEFAULT_TASK_COUNT,
        additional_options={"elasticsearch": {"xpack_enabled": xpack_enabled}},
        test_version_additional_options={
            "service": {"update_strategy": "parallel"},
            "elasticsearch": {"xpack_security_enabled": xpack_security_enabled},
        },
    )

    wait_for_expected_nodes_to_exist(service_name=service_name, task_count=DEFAULT_TASK_COUNT)


def _master_zero_http_port(service_name):
    """Returns a master node hostname+port endpoint that can be queried from within the cluster. We
    cannot cache this value because while the hostnames remain static, the ports are dynamic and may
    change if the master is replaced.

    """
    dns = sdk_networks.get_endpoint(PACKAGE_NAME, service_name, "master-http")["dns"]
    # 'dns' array will look something like this in CCM: [
    #   "master-0-node.[svcname].[...autoip...]:1027",
    #   "master-1-node.[svcname].[...autoip...]:1026",
    #   "master-2-node.[svcname].[...autoip...]:1025"
    # ]

    port = dns[0].split(":")[-1]
    log.info("Extracted {} as port for {}".format(port, dns[0]))
    return port
