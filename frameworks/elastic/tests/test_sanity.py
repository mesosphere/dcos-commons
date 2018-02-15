import logging

import pytest
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_metrics
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils
import shakedown
from tests import config

log = logging.getLogger(__name__)

foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
current_expected_task_count = config.DEFAULT_TASK_COUNT


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        log.info("Ensure elasticsearch and kibana are uninstalled...")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        sdk_upgrade.test_upgrade(
            config.PACKAGE_NAME,
            foldered_name,
            current_expected_task_count,
            additional_options={
                "service": {"name": foldered_name} })

        yield  # let the test session execute
    finally:
        log.info("Clean up elasticsearch and kibana...")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.fixture(autouse=True)
def pre_test_setup():
    sdk_tasks.check_running(foldered_name, current_expected_task_count)
    config.wait_for_expected_nodes_to_exist(service_name=foldered_name, task_count=current_expected_task_count)


@pytest.fixture
def default_populated_index():
    config.delete_index(config.DEFAULT_INDEX_NAME, service_name=foldered_name)
    config.create_index(config.DEFAULT_INDEX_NAME, config.DEFAULT_SETTINGS_MAPPINGS, service_name=foldered_name)
    config.create_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1, {
                           "name": "Loren", "role": "developer"},
                           service_name=foldered_name)


@pytest.mark.smoke
def test_service_health():
    assert shakedown.service_healthy(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_pod_replace_then_immediate_config_update():
    plugin_name = 'analysis-phonetic'

    cfg = sdk_marathon.get_config(foldered_name)
    cfg['env']['TASKCFG_ALL_ELASTICSEARCH_PLUGINS'] = plugin_name
    cfg['env']['UPDATE_STRATEGY'] = 'parallel'

    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod replace data-0')

    # issue config update immediately
    sdk_marathon.update_app(foldered_name, cfg)

    # ensure all nodes, especially data-0, get launched with the updated config
    config.check_plugin_installed(plugin_name, service_name=foldered_name)
    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.mesos_v0
def test_mesos_v0_api():
    prior_api_version = sdk_marathon.get_mesos_api_version(foldered_name)
    if prior_api_version is not "V0":
        sdk_marathon.set_mesos_api_version(foldered_name, "V0")
        sdk_marathon.set_mesos_api_version(foldered_name, prior_api_version)

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.sanity
def test_endpoints():
    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    for endpoint in config.ENDPOINT_TYPES:
        endpoints = sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'endpoints {}'.format(endpoint), json=True)
        host = endpoint.split('-')[0] # 'coordinator-http' => 'coordinator'
        assert endpoints['dns'][0].startswith(sdk_hosts.autoip_host(foldered_name, host + '-0-node'))
        assert endpoints['vip'].startswith(sdk_hosts.vip_host(foldered_name, host))

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.sanity
def test_indexing(default_populated_index):
    indices_stats = config.get_elasticsearch_indices_stats(config.DEFAULT_INDEX_NAME, service_name=foldered_name)
    assert indices_stats["_all"]["primaries"]["docs"]["count"] == 1
    doc = config.get_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1, service_name=foldered_name)
    assert doc["_source"]["name"] == "Loren"

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.sanity
@pytest.mark.metrics
@pytest.mark.dcos_min_version('1.9')
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
        foldered_name,
        "data-0-node",
        config.DEFAULT_ELASTIC_TIMEOUT,
        expected_metrics_exist)

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.sanity
def test_custom_yaml_base64():
    # apply this custom YAML block as a base64-encoded string:
    # cluster:
    #   routing:
    #     allocation:
    #       node_initial_primaries_recoveries: 3
    # The default value is 4. We're just testing to make sure the YAML formatting survived intact and the setting
    # got updated in the config.
    base64_str = 'Y2x1c3RlcjoNCiAgcm91dGluZzoNCiAgICBhbGxvY2F0aW9uOg0KIC' \
                 'AgICAgbm9kZV9pbml0aWFsX3ByaW1hcmllc19yZWNvdmVyaWVzOiAz'

    config.update_app(foldered_name, {'CUSTOM_YAML_BLOCK_BASE64': base64_str}, current_expected_task_count)
    config.check_custom_elasticsearch_cluster_setting(service_name=foldered_name)
    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.sanity
@pytest.mark.timeout(60 * 60)
def test_xpack_toggle_with_kibana(default_populated_index):
    log.info("\n***** Verify X-Pack disabled by default in elasticsearch")
    config.verify_commercial_api_status(False, service_name=foldered_name)

    log.info("\n***** Test kibana with X-Pack disabled...")
    elasticsearch_url = "http://" + sdk_hosts.vip_host(foldered_name, "coordinator", 9200)
    sdk_install.install(
        config.KIBANA_PACKAGE_NAME,
        config.KIBANA_PACKAGE_NAME,
        0,
        { "kibana": {
            "elasticsearch_url": elasticsearch_url
        }},
        timeout_seconds=config.DEFAULT_KIBANA_TIMEOUT,
        wait_for_deployment=False,
        insert_strict_options=False)
    config.check_kibana_adminrouter_integration(
        "service/{}/".format(config.KIBANA_PACKAGE_NAME))
    log.info("Uninstall kibana with X-Pack disabled")
    sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)

    log.info("\n***** Set/verify X-Pack enabled in elasticsearch. Requires parallel upgrade strategy for full restart.")
    config.set_xpack(True, service_name=foldered_name)
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
    log.info("\n***** Installing Kibana w/X-Pack can exceed default 15 minutes for Marathon "
             "deployment to complete due to a configured HTTP health check. (typical: 12 minutes)")
    sdk_install.install(
        config.KIBANA_PACKAGE_NAME,
        config.KIBANA_PACKAGE_NAME,
        0,
        { "kibana": {
            "elasticsearch_url": elasticsearch_url,
            "xpack_enabled": True
        }},
        timeout_seconds=config.DEFAULT_KIBANA_TIMEOUT,
        wait_for_deployment=False,
        insert_strict_options=False)
    config.check_kibana_adminrouter_integration("service/{}/login".format(config.KIBANA_PACKAGE_NAME))
    log.info("\n***** Uninstall kibana with X-Pack enabled")
    sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)

    log.info("\n***** Disable X-Pack in elasticsearch.")
    config.set_xpack(False, service_name=foldered_name)
    log.info("\n***** Verify we can still read what we wrote when X-Pack was enabled.")
    config.verify_commercial_api_status(False, service_name=foldered_name)
    doc = config.get_document(config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 2, service_name=foldered_name)
    assert doc["_source"]["name"] == "X-Pack"

    # reset upgrade strategy to serial
    config.update_app(foldered_name, {'UPDATE_STRATEGY': 'serial'}, current_expected_task_count)

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_losing_and_regaining_index_health(default_populated_index):
    config.check_elasticsearch_index_health(config.DEFAULT_INDEX_NAME, "green", service_name=foldered_name)
    shakedown.kill_process_on_host(sdk_hosts.system_host(foldered_name, "data-0-node"), "data__.*Elasticsearch")
    config.check_elasticsearch_index_health(config.DEFAULT_INDEX_NAME, "yellow", service_name=foldered_name)
    config.check_elasticsearch_index_health(config.DEFAULT_INDEX_NAME, "green", service_name=foldered_name)

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_master_reelection():
    initial_master = config.get_elasticsearch_master(service_name=foldered_name)
    shakedown.kill_process_on_host(sdk_hosts.system_host(foldered_name, initial_master), "master__.*Elasticsearch")
    sdk_plan.wait_for_in_progress_recovery(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)
    config.wait_for_expected_nodes_to_exist(service_name=foldered_name)
    new_master = config.get_elasticsearch_master(service_name=foldered_name)
    assert new_master.startswith("master") and new_master != initial_master

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_master_node_replace():
    # Ideally, the pod will get placed on a different agent. This test will verify that the remaining two masters
    # find the replaced master at its new IP address. This requires a reasonably low TTL for Java DNS lookups.
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod replace master-0')
    sdk_plan.wait_for_in_progress_recovery(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_data_node_replace():
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod replace data-0')
    sdk_plan.wait_for_in_progress_recovery(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_coordinator_node_replace():
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, 'pod replace coordinator-0')
    sdk_plan.wait_for_in_progress_recovery(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
@pytest.mark.timeout(60 * 60)
def test_plugin_install_and_uninstall(default_populated_index):
    plugin_name = 'analysis-phonetic'
    config.update_app(foldered_name, {'TASKCFG_ALL_ELASTICSEARCH_PLUGINS': plugin_name}, current_expected_task_count)
    config.check_plugin_installed(plugin_name, service_name=foldered_name)

    config.update_app(foldered_name, {'TASKCFG_ALL_ELASTICSEARCH_PLUGINS': ''}, current_expected_task_count)
    config.check_plugin_uninstalled(plugin_name, service_name=foldered_name)
    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
@pytest.mark.skip(reason="INFINITY-3216")
def test_unchanged_scheduler_restarts_without_restarting_tasks():
    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)
    initial_task_ids = sdk_tasks.get_task_ids(foldered_name, '')
    shakedown.kill_process_on_host(sdk_marathon.get_scheduler_host(foldered_name), "elastic.scheduler.Main")
    sdk_tasks.check_tasks_not_updated(foldered_name, '', initial_task_ids)
    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_bump_node_counts():
    # bump ingest and coordinator, but NOT data, which is bumped in the following test.
    # we want to avoid adding two data nodes because the cluster sometimes won't have enough room for it
    marathon_config = sdk_marathon.get_config(foldered_name)
    ingest_nodes = int(marathon_config['env']['INGEST_NODE_COUNT'])
    marathon_config['env']['INGEST_NODE_COUNT'] = str(ingest_nodes + 1)
    coordinator_nodes = int(marathon_config['env']['COORDINATOR_NODE_COUNT'])
    marathon_config['env']['COORDINATOR_NODE_COUNT'] = str(coordinator_nodes + 1)
    sdk_marathon.update_app(foldered_name, marathon_config)
    sdk_plan.wait_for_completed_deployment(foldered_name)
    global current_expected_task_count
    current_expected_task_count += 2
    sdk_tasks.check_running(foldered_name, current_expected_task_count)
    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_adding_data_node_only_restarts_masters():
    initial_master_task_ids = sdk_tasks.get_task_ids(foldered_name, "master")
    initial_data_task_ids = sdk_tasks.get_task_ids(foldered_name, "data")
    initial_coordinator_task_ids = sdk_tasks.get_task_ids(foldered_name, "coordinator")
    marathon_config = sdk_marathon.get_config(foldered_name)
    data_nodes = int(marathon_config['env']['DATA_NODE_COUNT'])
    marathon_config['env']['DATA_NODE_COUNT'] = str(data_nodes + 1)
    sdk_marathon.update_app(foldered_name, marathon_config)
    sdk_plan.wait_for_completed_deployment(foldered_name)
    global current_expected_task_count
    current_expected_task_count += 1
    sdk_tasks.check_running(foldered_name, current_expected_task_count)
    sdk_tasks.check_tasks_updated(foldered_name, "master", initial_master_task_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "data", initial_data_task_ids)
    sdk_tasks.check_tasks_not_updated(foldered_name, "coordinator", initial_coordinator_task_ids)
    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)
