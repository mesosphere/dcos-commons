import pytest

from tests.test_utils import *

DEFAULT_INDEX_NAME = 'customer'
DEFAULT_INDEX_TYPE = 'entry'
DEFAULT_NUMBER_OF_SHARDS = 1
DEFAULT_NUMBER_OF_REPLICAS = 1
DEFAULT_SETTINGS_MAPPINGS = {
    "settings": {
        "number_of_shards": DEFAULT_NUMBER_OF_SHARDS,
        "number_of_replicas": DEFAULT_NUMBER_OF_REPLICAS},
    "mappings": {
        DEFAULT_INDEX_TYPE: {
            "properties": {
                "name": {"type": "keyword"},
                "role": {"type": "keyword"}}}}}


@pytest.fixture
def default_populated_index():
    delete_index(DEFAULT_INDEX_NAME)
    create_index(DEFAULT_INDEX_NAME,
                 DEFAULT_SETTINGS_MAPPINGS)
    create_document(DEFAULT_INDEX_NAME, DEFAULT_INDEX_TYPE, 1, {"name": "Loren", "role": "developer"})


@pytest.mark.sanity
def test_tasks_health():
    check_dcos_tasks_health(DEFAULT_TASK_COUNT)


@pytest.mark.sanity
def test_service_health():
    check_dcos_service_health()


@pytest.mark.sanity
def test_expected_nodes_exist():
    cluster_health = get_elasticsearch_index_health(DEFAULT_INDEX_NAME)

    assert cluster_health["number_of_nodes"] == DEFAULT_NODE_COUNT


@pytest.mark.sanity
def test_indexing(default_populated_index):
    indices_stats = get_elasticsearch_indices_stats(DEFAULT_INDEX_NAME)
    assert indices_stats["_all"]["primaries"]["docs"]["count"] == 1
    doc = get_document(DEFAULT_INDEX_NAME, DEFAULT_INDEX_TYPE, 1)
    assert doc["_source"]["name"] == "Loren"


@pytest.mark.sanity
def test_commercial_api_available(default_populated_index):
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
    assert response["failures"] == []


@pytest.mark.sanity
def test_losing_and_regaining_index_health(default_populated_index):
    check_elasticsearch_index_health(DEFAULT_INDEX_NAME, "green")
    shakedown.kill_process_on_host("data-0.{}.mesos".format(PACKAGE_NAME), "node.data=true")
    check_elasticsearch_index_health(DEFAULT_INDEX_NAME, "yellow")
    check_elasticsearch_index_health(DEFAULT_INDEX_NAME, "green")


@pytest.mark.sanity
def test_master_reelection():
    initial_master = get_elasticsearch_master()
    shakedown.kill_process_on_host("{}.{}.mesos".format(initial_master, PACKAGE_NAME), "node.master=true")
    check_new_elasticsearch_master_elected(initial_master)


@pytest.mark.plugins
def test_plugin_install_and_uninstall(default_populated_index):
    plugin_name = 'analysis-phonetic'
    config = get_elasticsearch_config()
    config['env']['ELASTICSEARCH_PLUGINS'] = plugin_name
    marathon_update(config)
    check_plugin_installed(plugin_name)

    config = get_elasticsearch_config()
    config['env']['ELASTICSEARCH_PLUGINS'] = ""
    marathon_update(config)
    check_plugin_uninstalled(plugin_name)


@pytest.mark.bump
def test_bump_data_nodes():
    config = get_elasticsearch_config()
    data_nodes = int(config['env']['DATA_NODE_COUNT'])
    config['env']['DATA_NODE_COUNT'] = str(data_nodes + 1)
    marathon_update(config)

    check_dcos_tasks_health(DEFAULT_TASK_COUNT + 1)


@pytest.mark.ports
def test_change_master_ports(default_populated_index):
    config = get_elasticsearch_config()
    master_transport_port = int(config['env']['MASTER_NODE_TRANSPORT_PORT'])
    new_master_transport_port = master_transport_port + 73
    config['env']['MASTER_NODE_TRANSPORT_PORT'] = str(new_master_transport_port)
    master_http_port = int(config['env']['MASTER_NODE_HTTP_PORT'])
    new_master_http_port = master_http_port + 73
    config['env']['MASTER_NODE_HTTP_PORT'] = str(new_master_http_port)
    marathon_update(config)
    check_elasticsearch_index_health(DEFAULT_INDEX_NAME, "green", new_master_http_port)
