import time

import pytest

import sdk_install as install
import sdk_marathon as marathon
import sdk_tasks as tasks
import sdk_test_upgrade
import sdk_utils as utils
from tests.config import *

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


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)
    wait_for_expected_nodes_to_exist()


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.fixture
def default_populated_index():
    delete_index(DEFAULT_INDEX_NAME)
    create_index(DEFAULT_INDEX_NAME, DEFAULT_SETTINGS_MAPPINGS)
    create_document(DEFAULT_INDEX_NAME, DEFAULT_INDEX_TYPE, 1, {"name": "Loren", "role": "developer"})


@pytest.mark.sanity
@pytest.mark.smoke
def test_service_health():
    check_dcos_service_health()


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


@pytest.mark.recovery
@pytest.mark.sanity
def test_losing_and_regaining_index_health(default_populated_index):
    check_elasticsearch_index_health(DEFAULT_INDEX_NAME, "green")
    shakedown.kill_process_on_host("data-0-node.{}.mesos".format(PACKAGE_NAME), "data__.*Elasticsearch")
    check_elasticsearch_index_health(DEFAULT_INDEX_NAME, "yellow")
    check_elasticsearch_index_health(DEFAULT_INDEX_NAME, "green")


@pytest.mark.recovery
@pytest.mark.sanity
def test_master_reelection():
    initial_master = get_elasticsearch_master()
    shakedown.kill_process_on_host("{}.{}.mesos".format(initial_master, PACKAGE_NAME), "master__.*Elasticsearch")
    # Master re-election can take up to 3 seconds by default
    time.sleep(3)
    new_master = get_elasticsearch_master()
    assert new_master.startswith("master") and new_master != initial_master


@pytest.mark.recovery
@pytest.mark.sanity
def test_plugin_install_and_uninstall(default_populated_index):
    plugin_name = 'analysis-phonetic'
    config = marathon.get_config(PACKAGE_NAME)
    config['env']['ELASTICSEARCH_PLUGINS'] = plugin_name
    marathon.update_app(PACKAGE_NAME, config)
    check_plugin_installed(plugin_name)

    config = marathon.get_config(PACKAGE_NAME)
    config['env']['ELASTICSEARCH_PLUGINS'] = ""
    marathon.update_app(PACKAGE_NAME, config)
    check_plugin_uninstalled(plugin_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_unchanged_scheduler_restarts_without_restarting_tasks():
    initial_task_ids = tasks.get_task_ids(PACKAGE_NAME, "master")
    shakedown.kill_process_on_host(marathon.get_scheduler_host(PACKAGE_NAME), "elastic.scheduler.Main")
    tasks.check_tasks_not_updated(PACKAGE_NAME, "master", initial_task_ids)


@pytest.mark.sanity
def test_kibana_proxylite_adminrouter_integration():
    # run this test as late as possible, as it takes 10+ minutes for kibana to be ready
    check_kibana_proxylite_adminrouter_integration()


@pytest.mark.upgrade
@pytest.mark.sanity
def test_upgrade_downgrade():
    options = {
        "service": {
            "beta-optin": True
        }
    }
    sdk_test_upgrade.upgrade_downgrade("beta-{}".format(PACKAGE_NAME), PACKAGE_NAME, DEFAULT_TASK_COUNT,
                                       additional_options=options)


@pytest.mark.recovery
@pytest.mark.sanity
def test_bump_node_counts():
    # Run this test last, as it changes the task count
    config = marathon.get_config(PACKAGE_NAME)
    data_nodes = int(config['env']['DATA_NODE_COUNT'])
    config['env']['DATA_NODE_COUNT'] = str(data_nodes + 1)
    ingest_nodes = int(config['env']['INGEST_NODE_COUNT'])
    config['env']['INGEST_NODE_COUNT'] = str(ingest_nodes + 1)
    coordinator_nodes = int(config['env']['COORDINATOR_NODE_COUNT'])
    config['env']['COORDINATOR_NODE_COUNT'] = str(coordinator_nodes + 1)
    marathon.update_app(PACKAGE_NAME, config)
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT + 3)
