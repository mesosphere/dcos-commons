import logging

import pytest
import sdk_cmd
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


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
        log.info("Ensure elasticsearch and kibana are uninstalled...")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        sdk_upgrade.test_upgrade(
            config.PACKAGE_NAME,
            foldered_name,
            config.DEFAULT_TASK_COUNT,
            additional_options={
                "service": {"name": foldered_name},
                "ingest_nodes": {"count": 1} })

        yield  # let the test session execute
    finally:
        log.info("Clean up elasticsearch and kibana...")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.fixture(autouse=True)
def pre_test_setup():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    sdk_tasks.check_running(foldered_name, config.DEFAULT_TASK_COUNT)
    config.wait_for_expected_nodes_to_exist(service_name=foldered_name)


@pytest.fixture
def default_populated_index():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.delete_index(config.DEFAULT_INDEX_NAME, service_name=foldered_name)
    config.create_index(config.DEFAULT_INDEX_NAME, config.DEFAULT_SETTINGS_MAPPINGS, service_name=foldered_name)
    config.create_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1, {
                           "name": "Loren", "role": "developer"},
                           service_name=foldered_name)


@pytest.mark.focus
@pytest.mark.smoke
def test_service_health():
    assert shakedown.service_healthy(sdk_utils.get_foldered_name(config.SERVICE_NAME))


@pytest.mark.sanity
def test_endpoints():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    for endpoint in config.ENDPOINT_TYPES:
        endpoints = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'endpoints {}'.format(endpoint), json=True)
        host = endpoint.split('-')[0] # 'coordinator-http' => 'coordinator'
        assert endpoints['dns'][0].startswith(sdk_hosts.autoip_host(foldered_name, host + '-0-node'))
        assert endpoints['vip'].startswith(sdk_hosts.vip_host(foldered_name, host))


@pytest.mark.sanity
def test_indexing(default_populated_index):
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    indices_stats = config.get_elasticsearch_indices_stats(config.DEFAULT_INDEX_NAME, service_name=foldered_name)
    assert indices_stats["_all"]["primaries"]["docs"]["count"] == 1
    doc = config.get_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1, service_name=foldered_name)
    assert doc["_source"]["name"] == "Loren"


@pytest.mark.sanity
@pytest.mark.metrics
@sdk_utils.dcos_1_9_or_higher
def test_metrics():
    expected_metrics = [
        "node.data-0-node.fs.total.total_in_bytes",
        "node.data-0-node.jvm.mem.pools.old.peak_used_in_bytes",
        "node.data-0-node.jvm.threads.count"
    ]

    def expected_metrics_exist(emitted_metrics):
        # Elastic metrics are also dynamic and based on the service name# For eg:
        # elasticsearch.test__integration__elastic.node.data-0-node.thread_pool.listener.completed
        # To prevent this from breaking we drop the service name from the metric name
        # => data-0-node.thread_pool.listener.completed
        metric_names = ['.'.join(metric_name.split('.')[2:]) for metric_name in emitted_metrics]
        return sdk_metrics.check_metrics_presence(metric_names, expected_metrics)

    sdk_metrics.wait_for_service_metrics(
        config.PACKAGE_NAME,
        sdk_utils.get_foldered_name(config.SERVICE_NAME),
        "data-0-node",
        config.DEFAULT_ELASTIC_TIMEOUT,
        expected_metrics_exist
    )


@pytest.mark.sanity
@pytest.mark.timeout(60 * 60)
def test_xpack_toggle_with_kibana(default_populated_index):
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    log.info("\n***** Verify X-Pack disabled by default in elasticsearch")
    config.verify_commercial_api_status(False, service_name=foldered_name)

    log.info("\n***** Test kibana with X-Pack disabled...")
    shakedown.install_package(config.KIBANA_PACKAGE_NAME, options_json={
        "kibana": {"elasticsearch_url": "http://" + sdk_hosts.vip_host(foldered_name, "coordinator", 9200)}})
    shakedown.deployment_wait(
        app_id="/{}".format(config.KIBANA_PACKAGE_NAME), timeout=config.DEFAULT_KIBANA_TIMEOUT)
    config.check_kibana_adminrouter_integration(
        "service/{}/".format(config.KIBANA_PACKAGE_NAME))
    log.info("Uninstall kibana with X-Pack disabled")
    sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)

    log.info("\n***** Set/verify X-Pack enabled in elasticsearch")
    config.enable_xpack(service_name=foldered_name)
    config.verify_commercial_api_status(True, service_name=foldered_name)
    config.verify_xpack_license(service_name=foldered_name)

    log.info("\n***** Write some data while enabled, disable X-Pack, and verify we can still read what we wrote.")
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        2,
        {"name": "X-Pack", "role": "commercial plugin"},
        service_name=foldered_name)

    log.info("\n***** Test kibana with X-Pack enabled...")
    shakedown.install_package(config.KIBANA_PACKAGE_NAME, options_json={
        "kibana": {
            "elasticsearch_url": "http://" + sdk_hosts.vip_host(foldered_name, "coordinator", 9200),
            "xpack_enabled": True
        }})
    log.info("\n***** Installing Kibana w/X-Pack can take as much as 15 minutes for Marathon deployment ")
    log.info("to complete due to a configured HTTP health check. (typical: 12 minutes)")
    shakedown.deployment_wait(app_id="/{}".format(config.KIBANA_PACKAGE_NAME), timeout=config.DEFAULT_KIBANA_TIMEOUT)
    config.check_kibana_adminrouter_integration("service/{}/login".format(config.KIBANA_PACKAGE_NAME))
    log.info("\n***** Uninstall kibana with X-Pack enabled")
    sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)

    log.info("\n***** Disable X-Pack in elasticsearch.")
    config.disable_xpack(service_name=foldered_name)
    log.info("\n***** Verify we can still read what we wrote when X-Pack was enabled.")
    config.verify_commercial_api_status(False, service_name=foldered_name)
    doc = config.get_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 2, service_name=foldered_name)
    assert doc["_source"]["name"] == "X-Pack"


@pytest.mark.recovery
@pytest.mark.sanity
def test_losing_and_regaining_index_health(default_populated_index):
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    config.check_elasticsearch_index_health(config.DEFAULT_INDEX_NAME, "green", service_name=foldered_name)
    shakedown.kill_process_on_host(sdk_hosts.system_host(foldered_name, "data-0-node"), "data__.*Elasticsearch")
    config.check_elasticsearch_index_health(config.DEFAULT_INDEX_NAME, "yellow", service_name=foldered_name)
    config.check_elasticsearch_index_health(config.DEFAULT_INDEX_NAME, "green", service_name=foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_master_reelection():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    initial_master = config.get_elasticsearch_master(service_name=foldered_name)
    shakedown.kill_process_on_host(sdk_hosts.system_host(foldered_name, initial_master), "master__.*Elasticsearch")
    config.wait_for_expected_nodes_to_exist(service_name=foldered_name)
    new_master = config.get_elasticsearch_master(service_name=foldered_name)
    assert new_master.startswith("master") and new_master != initial_master


@pytest.mark.recovery
@pytest.mark.sanity
def test_master_node_replace():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    # Ideally, the pod will get placed on a different agent. This test will verify that the remaining two masters
    # find the replaced master at its new IP address. This requires a reasonably low TTL for Java DNS lookups.
    master_ids = sdk_tasks.get_task_ids(foldered_name, 'master-0')
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod replace master-0')
    sdk_tasks.check_tasks_updated(foldered_name, 'master-0', master_ids)
    # pre_test_setup will verify that the cluster becomes healthy again.


@pytest.mark.recovery
@pytest.mark.sanity
def test_data_node_replace():
    data_ids = sdk_tasks.get_task_ids(sdk_utils.get_foldered_name(config.SERVICE_NAME), 'data-0')
    sdk_cmd.svc_cli(config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME), 'pod replace data-0')
    sdk_tasks.check_tasks_updated(sdk_utils.get_foldered_name(config.SERVICE_NAME), 'data-0', data_ids)
    # pre_test_setup will verify that the cluster becomes healthy again.


@pytest.mark.recovery
@pytest.mark.sanity
def test_coordinator_node_replace():
    coordinator_ids = sdk_tasks.get_task_ids(sdk_utils.get_foldered_name(config.SERVICE_NAME), 'coordinator-0')
    sdk_cmd.svc_cli(config.PACKAGE_NAME, sdk_utils.get_foldered_name(config.SERVICE_NAME), 'pod replace coordinator-0')
    sdk_tasks.check_tasks_updated(sdk_utils.get_foldered_name(config.SERVICE_NAME), 'coordinator-0', coordinator_ids)
    # pre_test_setup will verify that the cluster becomes healthy again.


@pytest.mark.recovery
@pytest.mark.sanity
def test_plugin_install_and_uninstall(default_populated_index):
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    plugin_name = 'analysis-phonetic'
    marathon_config = sdk_marathon.get_config(foldered_name)
    marathon_config['env']['TASKCFG_ALL_ELASTICSEARCH_PLUGINS'] = plugin_name
    sdk_marathon.update_app(foldered_name, marathon_config)
    config.check_plugin_installed(plugin_name, service_name=foldered_name)

    marathon_config = sdk_marathon.get_config(foldered_name)
    marathon_config['env']['TASKCFG_ALL_ELASTICSEARCH_PLUGINS'] = ""
    sdk_marathon.update_app(foldered_name, marathon_config)
    config.check_plugin_uninstalled(plugin_name, service_name=foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_unchanged_scheduler_restarts_without_restarting_tasks():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    initial_task_ids = sdk_tasks.get_task_ids(foldered_name, "master")
    shakedown.kill_process_on_host(sdk_marathon.get_scheduler_host(foldered_name), "elastic.scheduler.Main")
    sdk_tasks.check_tasks_not_updated(foldered_name, "master", initial_task_ids)


@pytest.mark.recovery
@pytest.mark.sanity
def test_bump_node_counts():
    foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
    # Run this test last, as it changes the task count
    marathon_config = sdk_marathon.get_config(foldered_name)
    data_nodes = int(marathon_config['env']['DATA_NODE_COUNT'])
    marathon_config['env']['DATA_NODE_COUNT'] = str(data_nodes + 1)
    ingest_nodes = int(marathon_config['env']['INGEST_NODE_COUNT'])
    marathon_config['env']['INGEST_NODE_COUNT'] = str(ingest_nodes + 1)
    coordinator_nodes = int(marathon_config['env']['COORDINATOR_NODE_COUNT'])
    marathon_config['env']['COORDINATOR_NODE_COUNT'] = str(coordinator_nodes + 1)
    sdk_marathon.update_app(foldered_name, marathon_config)
    sdk_tasks.check_running(foldered_name, config.DEFAULT_TASK_COUNT + 3)
