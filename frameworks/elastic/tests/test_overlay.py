import pytest

import sdk_install as install
import sdk_utils as utils
import sdk_networks as networks
from tests.config import *


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT,
                    additional_options=networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)


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
@pytest.mark.overlay
def test_service_health():
    assert shakedown.service_healthy(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
def test_indexing(default_populated_index):
    indices_stats = get_elasticsearch_indices_stats(DEFAULT_INDEX_NAME)
    observed_count = indices_stats["_all"]["primaries"]["docs"]["count"]
    assert observed_count == 1, "Indices has incorrect count should be 1, got {}".format(observed_count)
    doc = get_document(DEFAULT_INDEX_NAME, DEFAULT_INDEX_TYPE, 1)
    observed_name = doc["_source"]["name"]
    assert observed_name == "Loren", "Incorrect name, should be 'Loren' got {}".format(observed_name)


@pytest.mark.sanity
@pytest.mark.overlay
def test_tasks_on_overlay():
    elastic_tasks = shakedown.get_service_task_ids(PACKAGE_NAME)
    assert len(elastic_tasks) == DEFAULT_TASK_COUNT, \
        "Incorrect number of tasks should be {} got {}".format(DEFAULT_TASK_COUNT, len(elastic_tasks))
    for task in elastic_tasks:
        networks.check_task_network(task)


@pytest.mark.sanity
@pytest.mark.overlay
def test_endpoints_on_overlay():
    observed_endpoints = networks.get_and_test_endpoints("", PACKAGE_NAME, 4)
    expected_endpoints = ("coordinator",
                          "data",
                          "ingest",
                          "master")
    for endpoint in expected_endpoints:
        assert endpoint in observed_endpoints, "missing {} endpoint".format(endpoint)
        specific_endpoint = networks.get_and_test_endpoints(endpoint, PACKAGE_NAME, 4)
        networks.check_endpoints_on_overlay(specific_endpoint)

