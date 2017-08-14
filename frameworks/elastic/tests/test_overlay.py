import pytest
import sdk_install
import sdk_networks
import sdk_tasks
import sdk_utils
import shakedown
from tests import config


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME)

@pytest.fixture(autouse=True)
def pre_test_setup():
    sdk_tasks.check_running(config.PACKAGE_NAME, config.DEFAULT_TASK_COUNT)
    config.wait_for_expected_nodes_to_exist()


@pytest.fixture
def default_populated_index():
    config.delete_index(config.DEFAULT_INDEX_NAME)
    config.create_index(config.DEFAULT_INDEX_NAME, config.DEFAULT_SETTINGS_MAPPINGS)
    config.create_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1, {"name": "Loren", "role": "developer"})


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
@sdk_utils.dcos_1_9_or_higher
def test_service_health():
    assert shakedown.service_healthy(config.PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.overlay
@sdk_utils.dcos_1_9_or_higher
def test_indexing(default_populated_index):
    def fun():
        indices_stats = config.get_elasticsearch_indices_stats(config.DEFAULT_INDEX_NAME)
        observed_count = indices_stats["_all"]["primaries"]["docs"]["count"]
        assert observed_count == 1, "Indices has incorrect count: should be 1, got {}".format(observed_count)
        doc = get_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1)
        observed_name = doc["_source"]["name"]
        return observed_name == "Loren"

    return shakedown.wait_for(fun, timeout_seconds=config.DEFAULT_ELASTIC_TIMEOUT)


@pytest.mark.sanity
@pytest.mark.overlay
@sdk_utils.dcos_1_9_or_higher
def test_tasks_on_overlay():
    elastic_tasks = shakedown.get_service_task_ids(config.PACKAGE_NAME)
    assert len(elastic_tasks) == config.DEFAULT_TASK_COUNT, \
        "Incorrect number of tasks should be {} got {}".format(config.DEFAULT_TASK_COUNT, len(elastic_tasks))
    for task in elastic_tasks:
        sdk_networks.check_task_network(task)


@pytest.mark.sanity
@pytest.mark.overlay
@sdk_utils.dcos_1_9_or_higher
def test_endpoints_on_overlay():
    observed_endpoints = sdk_networks.get_and_test_endpoints("", config.PACKAGE_NAME, 8)
    for endpoint in config.ENDPOINT_TYPES:
        assert endpoint in observed_endpoints, "missing {} endpoint".format(endpoint)
        specific_endpoint = sdk_networks.get_and_test_endpoints(endpoint, config.PACKAGE_NAME, 3)
        sdk_networks.check_endpoints_on_overlay(specific_endpoint)
