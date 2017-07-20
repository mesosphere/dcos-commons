import os
import pytest
import shakedown

import sdk_install
import sdk_networks
import sdk_tasks
import sdk_utils

from tests.config import (PACKAGE_NAME,
                          DEFAULT_TASK_COUNT,
                          DEFAULT_INDEX_NAME,
                          DEFAULT_INDEX_TYPE,
                          DEFAULT_SETTINGS_MAPPINGS,
                          DEFAULT_ELASTIC_TIMEOUT,
                          wait_for_expected_nodes_to_exist,
                          delete_index,
                          create_index,
                          create_document,
                          get_elasticsearch_indices_stats,
                          get_document
                          )

overlay_nostrict = pytest.mark.skipif(os.environ.get("SECURITY") == "strict",
                                      reason="overlay tests currently broken in strict")


def setup_module(module):
    sdk_install.uninstall(PACKAGE_NAME)
    sdk_utils.gc_frameworks()
    sdk_install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT,
                    additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)


def setup_function(function):
    sdk_tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)
    wait_for_expected_nodes_to_exist()


def teardown_module(module):
    sdk_install.uninstall(PACKAGE_NAME)


@pytest.fixture
def default_populated_index():
    delete_index(DEFAULT_INDEX_NAME)
    create_index(DEFAULT_INDEX_NAME, DEFAULT_SETTINGS_MAPPINGS)
    create_document(DEFAULT_INDEX_NAME, DEFAULT_INDEX_TYPE, 1, {"name": "Loren", "role": "developer"})


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
@overlay_nostrict
@sdk_utils.dcos_1_9_or_higher
def test_service_health():
    assert shakedown.service_healthy(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
@overlay_nostrict
@sdk_utils.dcos_1_9_or_higher
def test_indexing(default_populated_index):
    def fun():
        indices_stats = get_elasticsearch_indices_stats(DEFAULT_INDEX_NAME)
        observed_count = indices_stats["_all"]["primaries"]["docs"]["count"]
        assert observed_count == 1, "Indices has incorrect count: should be 1, got {}".format(observed_count)
        doc = get_document(DEFAULT_INDEX_NAME, DEFAULT_INDEX_TYPE, 1)
        observed_name = doc["_source"]["name"]
        return observed_name == "Loren"

    return shakedown.wait_for(fun, timeout_seconds=DEFAULT_ELASTIC_TIMEOUT)


@pytest.mark.sanity
@pytest.mark.overlay
@overlay_nostrict
@sdk_utils.dcos_1_9_or_higher
def test_tasks_on_overlay():
    elastic_tasks = shakedown.get_service_task_ids(PACKAGE_NAME)
    assert len(elastic_tasks) == DEFAULT_TASK_COUNT, \
        "Incorrect number of tasks should be {} got {}".format(DEFAULT_TASK_COUNT, len(elastic_tasks))
    for task in elastic_tasks:
        sdk_networks.check_task_network(task)


@pytest.mark.sanity
@pytest.mark.overlay
@overlay_nostrict
@sdk_utils.dcos_1_9_or_higher
def test_endpoints_on_overlay():
    observed_endpoints = sdk_networks.get_and_test_endpoints("", PACKAGE_NAME, 8)
    expected_endpoints = (
        'coordinator-http', 'coordinator-transport',
        'data-http', 'data-transport',
        'ingest-http', 'ingest-transport',
        'master-http', 'master-transport')
    for endpoint in expected_endpoints:
        assert endpoint in observed_endpoints, "missing {} endpoint".format(endpoint)
        specific_endpoint = sdk_networks.get_and_test_endpoints(endpoint, PACKAGE_NAME, 3)
        sdk_networks.check_endpoints_on_overlay(specific_endpoint)
