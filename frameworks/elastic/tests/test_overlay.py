import pytest
import retrying
import sdk_install
import sdk_networks
import sdk_tasks
from tests import config


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS,
        )
        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)



@pytest.fixture(autouse=True)
def pre_test_setup():
    sdk_tasks.check_running(config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)
    config.wait_for_expected_nodes_to_exist(task_count=config.DEFAULT_TASK_COUNT)


@pytest.fixture
def default_populated_index():
    config.delete_index(config.DEFAULT_INDEX_NAME)
    config.create_index(config.DEFAULT_INDEX_NAME, config.DEFAULT_SETTINGS_MAPPINGS)
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        1,
        {"name": "Loren", "role": "developer"},
    )

@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version('1.9')
@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=config.DEFAULT_TIMEOUT * 1000,
    retry_on_result=lambda res: not res,
)

def test_indexing(default_populated_index):
    indices_stats = config.get_elasticsearch_indices_stats(config.DEFAULT_INDEX_NAME)
    observed_count = indices_stats["_all"]["primaries"]["docs"]["count"]
    assert observed_count == 1, "Indices has incorrect count: should be 1, got {}".format(
        observed_count
    )
    doc = config.get_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1)
    observed_name = doc["_source"]["name"]
    return observed_name == "Loren"


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version("1.9")
def test_tasks_on_overlay():
    elastic_tasks = shakedown.get_service_task_ids(config.SERVICE_NAME)
    assert len(elastic_tasks) == config.DEFAULT_TASK_COUNT, \
        "Incorrect number of tasks should be {} got {}".format(config.DEFAULT_TASK_COUNT, len(elastic_tasks))
    for task in elastic_tasks:
        sdk_networks.check_task_network(task)


@pytest.mark.sanity
@pytest.mark.overlay
@pytest.mark.dcos_min_version('1.9')
def test_endpoints_on_overlay():
    endpoints_on_overlay_to_count = {
        "coordinator-http": 1,
        "coordinator-transport": 1,
        "data-http": 2,
        "data-transport": 2,
        "master-http": 3,
        "master-transport": 3,
    }

    endpoint_names = sdk_networks.get_endpoint_names(config.PACKAGE_NAME, config.SERVICE_NAME)
    assert set(endpoints_on_overlay_to_count.keys()) == set(endpoint_names)

    for endpoint_name, expected_count in endpoints_on_overlay_to_count.items():
        sdk_networks.check_endpoint_on_overlay(config.PACKAGE_NAME, config.SERVICE_NAME, endpoint_name, expected_count)
