import json
import logging

import pytest
import sdk_cmd as cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_metrics
import sdk_tasks
import sdk_upgrade
import sdk_utils
import shakedown
from tests import config

log = logging.getLogger(__name__)

FOLDERED_SERVICE_NAME = sdk_utils.get_foldered_name(config.PACKAGE_NAME)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        log.info("Ensure elasticsearch and kibana are uninstalled...")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(FOLDERED_SERVICE_NAME, package_name=config.PACKAGE_NAME)

        sdk_upgrade.test_upgrade(
            "beta-{}".format(config.PACKAGE_NAME),
            config.PACKAGE_NAME,
            config.DEFAULT_TASK_COUNT,
            service_name=FOLDERED_SERVICE_NAME,
            additional_options={"service": {"name": FOLDERED_SERVICE_NAME},
                                "ingest_nodes": {"count": 1}})

        yield  # let the test session execute
    finally:
        log.info("Clean up elasticsearch and kibana...")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(FOLDERED_SERVICE_NAME, package_name=config.PACKAGE_NAME)


@pytest.fixture(autouse=True)
def pre_test_setup():
    sdk_tasks.check_running(FOLDERED_SERVICE_NAME, config.DEFAULT_TASK_COUNT)
    config.wait_for_expected_nodes_to_exist(service_name=FOLDERED_SERVICE_NAME)


@pytest.fixture
def default_populated_index():
    config.delete_index(config.DEFAULT_INDEX_NAME, service_name=FOLDERED_SERVICE_NAME)
    config.create_index(config.DEFAULT_INDEX_NAME, config.DEFAULT_SETTINGS_MAPPINGS, service_name=FOLDERED_SERVICE_NAME)
    config.create_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1, {"name": "Loren", "role": "developer"}, service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.focus
@pytest.mark.smoke
def test_service_health():
    assert shakedown.service_healthy(FOLDERED_SERVICE_NAME)


@pytest.mark.sanity
def test_endpoints():
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    for endpoint in config.ENDPOINT_TYPES:
        endpoints = json.loads(cmd.run_cli('elastic --name={} endpoints {}'.format(FOLDERED_SERVICE_NAME, endpoint)))
        host = endpoint.split('-')[0] # 'coordinator-http' => 'coordinator'
        assert endpoints['dns'][0].startswith(sdk_hosts.autoip_host(FOLDERED_SERVICE_NAME, host + '-0-node'))
        assert endpoints['vip'].startswith(sdk_hosts.vip_host(FOLDERED_SERVICE_NAME, host))


@pytest.mark.sanity
def test_indexing(default_populated_index):
    indices_stats = config.get_elasticsearch_indices_stats(config.DEFAULT_INDEX_NAME, service_name=FOLDERED_SERVICE_NAME)
    assert indices_stats["_all"]["primaries"]["docs"]["count"] == 1
    doc = config.get_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1, service_name=FOLDERED_SERVICE_NAME)
    assert doc["_source"]["name"] == "Loren"


@pytest.mark.sanity
@pytest.mark.metrics
@sdk_utils.dcos_1_9_or_higher
def test_metrics():
    sdk_metrics.wait_for_any_metrics(FOLDERED_SERVICE_NAME, "data-0-node", config.DEFAULT_ELASTIC_TIMEOUT)


@pytest.mark.sanity
@pytest.mark.timeout(60 * 60)
def test_xpack_toggle_with_kibana(default_populated_index):
    log.info("\n***** Verify X-Pack disabled by default in elasticsearch")
    config.verify_commercial_api_status(False, service_name=FOLDERED_SERVICE_NAME)

    log.info("\n***** Test kibana with X-Pack disabled...")
    shakedown.install_package(config.KIBANA_PACKAGE_NAME, options_json={
        "kibana": {
            "elasticsearch_url": "http://" + sdk_hosts.vip_host(FOLDERED_SERVICE_NAME, "coordinator", 9200)
        }})
    shakedown.deployment_wait(app_id="/{}".format(config.KIBANA_PACKAGE_NAME), timeout=config.DEFAULT_KIBANA_TIMEOUT)
    config.check_kibana_adminrouter_integration("service/{}/".format(config.KIBANA_PACKAGE_NAME))
    log.info("Uninstall kibana with X-Pack disabled")
    sdk_install.uninstall(config.KIBANA_PACKAGE_NAME)

    log.info("\n***** Set/verify X-Pack enabled in elasticsearch")
    config.enable_xpack(service_name=FOLDERED_SERVICE_NAME)
    config.verify_commercial_api_status(True, service_name=FOLDERED_SERVICE_NAME)
    config.verify_xpack_license(service_name=FOLDERED_SERVICE_NAME)

    log.info("\n***** Write some data while enabled, disable X-Pack, and verify we can still read what we wrote.")
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        2,
        {"name": "X-Pack", "role": "commercial plugin"},
        service_name=FOLDERED_SERVICE_NAME)

    log.info("\n***** Test kibana with X-Pack enabled...")
    shakedown.install_package(config.KIBANA_PACKAGE_NAME, options_json={
        "kibana": {
            "elasticsearch_url": "http://" + sdk_hosts.vip_host(FOLDERED_SERVICE_NAME, "coordinator", 9200),
            "xpack_enabled": True
        }})
    log.info("\n***** Installing Kibana w/X-Pack can take as much as 15 minutes for Marathon deployment ")
    log.info("to complete due to a configured HTTP health check. (typical: 12 minutes)")
    shakedown.deployment_wait(app_id="/{}".format(config.KIBANA_PACKAGE_NAME), timeout=config.DEFAULT_KIBANA_TIMEOUT)
    config.check_kibana_adminrouter_integration("service/{}/login".format(config.KIBANA_PACKAGE_NAME))
    log.info("\n***** Uninstall kibana with X-Pack enabled")
    sdk_install.uninstall(config.KIBANA_PACKAGE_NAME)

    log.info("\n***** Disable X-Pack in elasticsearch.")
    config.disable_xpack(service_name=FOLDERED_SERVICE_NAME)
    log.info("\n***** Verify we can still read what we wrote when X-Pack was enabled.")
    config.verify_commercial_api_status(False, service_name=FOLDERED_SERVICE_NAME)
    doc = config.get_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 2, service_name=FOLDERED_SERVICE_NAME)
    assert doc["_source"]["name"] == "X-Pack"


@pytest.mark.recovery
@pytest.mark.sanity
def test_losing_and_regaining_index_health(default_populated_index):
    config.check_elasticsearch_index_health(config.DEFAULT_INDEX_NAME, "green", service_name=FOLDERED_SERVICE_NAME)
    shakedown.kill_process_on_host(sdk_hosts.system_host(FOLDERED_SERVICE_NAME, "data-0-node"), "data__.*Elasticsearch")
    config.check_elasticsearch_index_health(config.DEFAULT_INDEX_NAME, "yellow", service_name=FOLDERED_SERVICE_NAME)
    config.check_elasticsearch_index_health(config.DEFAULT_INDEX_NAME, "green", service_name=FOLDERED_SERVICE_NAME)


@pytest.mark.recovery
@pytest.mark.sanity
def test_master_reelection():
    initial_master = config.get_elasticsearch_master(service_name=FOLDERED_SERVICE_NAME)
    shakedown.kill_process_on_host(sdk_hosts.system_host(FOLDERED_SERVICE_NAME, initial_master), "master__.*Elasticsearch")
    config.wait_for_expected_nodes_to_exist(service_name=FOLDERED_SERVICE_NAME)
    new_master = config.get_elasticsearch_master(service_name=FOLDERED_SERVICE_NAME)
    assert new_master.startswith("master") and new_master != initial_master


@pytest.mark.recovery
@pytest.mark.sanity
def test_master_node_replace():
    # Ideally, the pod will get placed on a different agent. This test will verify that the remaining two masters
    # find the replaced master at its new IP address. This requires a reasonably low TTL for Java DNS lookups.
    master_ids = sdk_tasks.get_task_ids(FOLDERED_SERVICE_NAME, 'master-0')
    cmd.run_cli('elastic --name={} pod replace master-0'.format(FOLDERED_SERVICE_NAME))
    sdk_tasks.check_tasks_updated(FOLDERED_SERVICE_NAME, 'master-0', master_ids)
    # pre_test_setup will verify that the cluster becomes healthy again.


@pytest.mark.recovery
@pytest.mark.sanity
def test_plugin_install_and_uninstall(default_populated_index):
    plugin_name = 'analysis-phonetic'
    marathon_config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    marathon_config['env']['TASKCFG_ALL_ELASTICSEARCH_PLUGINS'] = plugin_name
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, marathon_config)
    config.check_plugin_installed(plugin_name, service_name=FOLDERED_SERVICE_NAME)

    marathon_config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    marathon_config['env']['TASKCFG_ALL_ELASTICSEARCH_PLUGINS'] = ""
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, marathon_config)
    config.check_plugin_uninstalled(plugin_name, service_name=FOLDERED_SERVICE_NAME)


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
    marathon_config = sdk_marathon.get_config(FOLDERED_SERVICE_NAME)
    data_nodes = int(marathon_config['env']['DATA_NODE_COUNT'])
    marathon_config['env']['DATA_NODE_COUNT'] = str(data_nodes + 1)
    ingest_nodes = int(marathon_config['env']['INGEST_NODE_COUNT'])
    marathon_config['env']['INGEST_NODE_COUNT'] = str(ingest_nodes + 1)
    coordinator_nodes = int(marathon_config['env']['COORDINATOR_NODE_COUNT'])
    marathon_config['env']['COORDINATOR_NODE_COUNT'] = str(coordinator_nodes + 1)
    sdk_marathon.update_app(FOLDERED_SERVICE_NAME, marathon_config)
    sdk_tasks.check_running(FOLDERED_SERVICE_NAME, config.DEFAULT_TASK_COUNT + 3)
