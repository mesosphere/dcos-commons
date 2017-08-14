import logging

import pytest

import sdk_cmd as cmd
import sdk_install
import sdk_metrics
import sdk_upgrade
import sdk_utils
from tests.config import *

log = logging.getLogger(__name__)

FOLDERED_SERVICE_NAME = sdk_utils.get_foldered_name(PACKAGE_NAME)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        log.info("Ensure elasticsearch and kibana are uninstalled...")
        sdk_install.uninstall(KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)

        sdk_upgrade.test_upgrade(
            "beta-{}".format(PACKAGE_NAME),
            PACKAGE_NAME,
            DEFAULT_TASK_COUNT,
            service_name=FOLDERED_SERVICE_NAME,
            additional_options={"service": {"name": FOLDERED_SERVICE_NAME}})

        yield  # let the test session execute
    finally:
        log.info("Clean up elasticsearch and kibana...")
        sdk_install.uninstall(KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)


@pytest.fixture(autouse=True)
def pre_test_setup():
    sdk_tasks.check_running(FOLDERED_SERVICE_NAME, DEFAULT_TASK_COUNT)
    wait_for_expected_nodes_to_exist(service_name=FOLDERED_SERVICE_NAME)


@pytest.fixture
def default_populated_index():
    delete_index(DEFAULT_INDEX_NAME, service_name=FOLDERED_SERVICE_NAME)
    create_index(DEFAULT_INDEX_NAME, DEFAULT_SETTINGS_MAPPINGS, service_name=FOLDERED_SERVICE_NAME)
    create_document(DEFAULT_INDEX_NAME, DEFAULT_INDEX_TYPE, 1, {"name": "Loren", "role": "developer"}, service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
def test_service_health():
    assert shakedown.service_healthy(FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
def test_endpoints():
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    for endpoint in ENDPOINT_TYPES:
        endpoints = json.loads(cmd.run_cli('elastic --name={} endpoints {}'.format(FOLDERED_SERVICE_NAME, endpoint)))
        host = endpoint.split('-')[0] # 'coordinator-http' => 'coordinator'
        assert endpoints['dns'][0].startswith(sdk_hosts.autoip_host(FOLDERED_SERVICE_NAME, host + '-0-node'))
        assert endpoints['vip'].startswith(sdk_hosts.vip_host(FOLDERED_SERVICE_NAME, host))


@pytest.mark.sanity
def test_indexing(default_populated_index):
    indices_stats = get_elasticsearch_indices_stats(DEFAULT_INDEX_NAME, service_name=FOLDERED_SERVICE_NAME)
    assert indices_stats["_all"]["primaries"]["docs"]["count"] == 1
    doc = get_document(DEFAULT_INDEX_NAME, DEFAULT_INDEX_TYPE, 1, service_name=FOLDERED_SERVICE_NAME)
    assert doc["_source"]["name"] == "Loren"


@pytest.mark.sanity
@pytest.mark.metrics
@sdk_utils.dcos_1_9_or_higher
def test_metrics():
    sdk_metrics.wait_for_any_metrics(FOLDERED_SERVICE_NAME, "data-0-node", DEFAULT_ELASTIC_TIMEOUT)


@pytest.mark.focus
@pytest.mark.sanity
@pytest.mark.timeout(60 * 60)
def test_xpack_toggle_with_kibana(default_populated_index):
    log.info("\n***** Verify X-Pack disabled by default in elasticsearch")
    verify_commercial_api_status(False, service_name=FOLDERED_SERVICE_NAME)

    log.info("\n***** Test kibana with X-Pack disabled...")
    shakedown.install_package(KIBANA_PACKAGE_NAME, options_json={
        "kibana": {
            "elasticsearch_url": "http://" + sdk_hosts.vip_host(FOLDERED_SERVICE_NAME, "coordinator", 9200)
        }})
    shakedown.deployment_wait(app_id="/{}".format(KIBANA_PACKAGE_NAME), timeout=DEFAULT_KIBANA_TIMEOUT)
    check_kibana_adminrouter_integration("service/{}/".format(KIBANA_PACKAGE_NAME))
    log.info("Uninstall kibana with X-Pack disabled")
    sdk_install.uninstall(KIBANA_PACKAGE_NAME)

    log.info("\n***** Set/verify X-Pack enabled in elasticsearch")
    enable_xpack(service_name=FOLDERED_SERVICE_NAME)
    verify_commercial_api_status(True, service_name=FOLDERED_SERVICE_NAME)
    verify_xpack_license(service_name=FOLDERED_SERVICE_NAME)

    log.info("\n***** Write some data while enabled, disable X-Pack, and verify we can still read what we wrote.")
    create_document(
        DEFAULT_INDEX_NAME,
        DEFAULT_INDEX_TYPE,
        2,
        {"name": "X-Pack", "role": "commercial plugin"},
        service_name=FOLDERED_SERVICE_NAME)

    log.info("\n***** Test kibana with X-Pack enabled...")
    shakedown.install_package(KIBANA_PACKAGE_NAME, options_json={
        "kibana": {
            "elasticsearch_url": "http://" + sdk_hosts.vip_host(FOLDERED_SERVICE_NAME, "coordinator", 9200),
            "xpack_enabled": True
        }})
    log.info("\n***** Installing Kibana w/X-Pack can take as much as 15 minutes for Marathon deployment ")
    log.info("to complete due to a configured HTTP health check. (typical: 12 minutes)")
    shakedown.deployment_wait(app_id="/{}".format(KIBANA_PACKAGE_NAME), timeout=DEFAULT_KIBANA_TIMEOUT)
    check_kibana_adminrouter_integration("service/{}/login".format(KIBANA_PACKAGE_NAME))
    log.info("\n***** Uninstall kibana with X-Pack enabled")
    sdk_install.uninstall(KIBANA_PACKAGE_NAME)

    log.info("\n***** Disable X-Pack in elasticsearch.")
    disable_xpack(service_name=FOLDERED_SERVICE_NAME)
    log.info("\n***** Verify we can still read what we wrote when X-Pack was enabled.")
    verify_commercial_api_status(False, service_name=FOLDERED_SERVICE_NAME)
    doc = get_document(DEFAULT_INDEX_NAME, DEFAULT_INDEX_TYPE, 2, service_name=FOLDERED_SERVICE_NAME)
    assert doc["_source"]["name"] == "X-Pack"


@pytest.mark.recovery
@pytest.mark.sanity
def test_losing_and_regaining_index_health(default_populated_index):
    check_elasticsearch_index_health(DEFAULT_INDEX_NAME, "green", service_name=FOLDERED_SERVICE_NAME)
    shakedown.kill_process_on_host(sdk_hosts.system_host(FOLDERED_SERVICE_NAME, "data-0-node"), "data__.*Elasticsearch")
    check_elasticsearch_index_health(DEFAULT_INDEX_NAME, "yellow", service_name=FOLDERED_SERVICE_NAME)
    check_elasticsearch_index_health(DEFAULT_INDEX_NAME, "green", service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.recovery
@pytest.mark.sanity
def test_master_reelection():
    initial_master = get_elasticsearch_master(service_name=FOLDERED_SERVICE_NAME)
    shakedown.kill_process_on_host(sdk_hosts.system_host(FOLDERED_SERVICE_NAME, initial_master), "master__.*Elasticsearch")
    wait_for_expected_nodes_to_exist(service_name=FOLDERED_SERVICE_NAME)
    new_master = get_elasticsearch_master(service_name=FOLDERED_SERVICE_NAME)
    assert new_master.startswith("master") and new_master != initial_master


@pytest.mark.recovery
@pytest.mark.sanity
def test_master_node_replace():
    # Ideally, the pod will get placed on a different agent. This test will verify that the remaining two masters
    # find the replaced master at its new IP address. This requires a reasonably low TTL for Java DNS lookups.
    cmd.run_cli('elastic --name={} pod replace master-0'.format(FOLDERED_SERVICE_NAME))
    # setup_function will verify that the cluster becomes healthy again.


@pytest.mark.recovery
@pytest.mark.sanity
def test_plugin_install_and_uninstall(default_populated_index):
    plugin_name = 'analysis-phonetic'
    config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    config['env']['TASKCFG_ALL_ELASTICSEARCH_PLUGINS'] = plugin_name
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, config)
    check_plugin_installed(plugin_name, service_name=FOLDERED_SERVICE_NAME)

    config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    config['env']['TASKCFG_ALL_ELASTICSEARCH_PLUGINS'] = ""
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, config)
    check_plugin_uninstalled(plugin_name, service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.recovery
@pytest.mark.sanity
def test_unchanged_scheduler_restarts_without_restarting_tasks():
    initial_task_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, "master")
    shakedown.kill_process_on_host(sdk_marathon.get_scheduler_host(FOLDERED_SERVICE_NAME), "elastic.scheduler.Main")
    sdk_tasks.check_tasks_not_updated(FOLDERED_SERVICE_NAME, "master", initial_task_ids)


@pytest.mark.recovery
@pytest.mark.sanity
def test_bump_node_counts():
    # Run this test last, as it changes the task count
    config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    data_nodes = int(config['env']['DATA_NODE_COUNT'])
    config['env']['DATA_NODE_COUNT'] = str(data_nodes + 1)
    ingest_nodes = int(config['env']['INGEST_NODE_COUNT'])
    config['env']['INGEST_NODE_COUNT'] = str(ingest_nodes + 1)
    coordinator_nodes = int(config['env']['COORDINATOR_NODE_COUNT'])
    config['env']['COORDINATOR_NODE_COUNT'] = str(coordinator_nodes + 1)
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, config)
    sdk_tasks.check_running(FOLDERED_SERVICE_NAME, DEFAULT_TASK_COUNT + 3)
