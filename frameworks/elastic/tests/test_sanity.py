import logging

from toolz import get_in
import pytest

import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_metrics
import sdk_networks
import sdk_plan
import sdk_service
import sdk_tasks
import sdk_upgrade
import sdk_utils
from tests import config

log = logging.getLogger(__name__)

foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
current_expected_task_count = config.DEFAULT_TASK_COUNT


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        log.info("Ensure elasticsearch and kibana are uninstalled...")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        sdk_upgrade.test_upgrade(
            config.PACKAGE_NAME,
            foldered_name,
            current_expected_task_count,
            additional_options={"service": {"name": foldered_name}},
        )

        yield  # let the test session execute
    finally:
        log.info("Clean up elasticsearch and kibana...")
        sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.fixture(autouse=True)
def pre_test_setup():
    sdk_tasks.check_running(foldered_name, current_expected_task_count)
    config.wait_for_expected_nodes_to_exist(
        service_name=foldered_name, task_count=current_expected_task_count
    )


@pytest.fixture
def default_populated_index():
    config.delete_index(config.DEFAULT_INDEX_NAME, service_name=foldered_name)
    config.create_index(
        config.DEFAULT_INDEX_NAME, config.DEFAULT_SETTINGS_MAPPINGS, service_name=foldered_name
    )
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        1,
        {"name": "Loren", "role": "developer"},
        service_name=foldered_name,
    )


@pytest.mark.recovery
@pytest.mark.sanity
def test_pod_replace_then_immediate_config_update():
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod replace data-0")

    plugins = "analysis-phonetic"

    sdk_service.update_configuration(
        config.PACKAGE_NAME,
        foldered_name,
        {"service": {"update_strategy": "parallel"}, "elasticsearch": {"plugins": plugins}},
        current_expected_task_count,
    )

    # Ensure all nodes, especially data-0, get launched with the updated config.
    config.check_elasticsearch_plugin_installed(plugins, service_name=foldered_name)
    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.sanity
def test_endpoints():
    # Check that we can reach the scheduler via admin router, and that returned endpoints are
    # sanitized.
    for endpoint in config.ENDPOINT_TYPES:
        endpoints = sdk_networks.get_endpoint(config.PACKAGE_NAME, foldered_name, endpoint)
        host = endpoint.split("-")[0]  # 'coordinator-http' => 'coordinator'
        assert endpoints["dns"][0].startswith(
            sdk_hosts.autoip_host(foldered_name, host + "-0-node")
        )
        assert endpoints["vip"].startswith(sdk_hosts.vip_host(foldered_name, host))

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.sanity
def test_indexing(default_populated_index):
    indices_stats = config.get_elasticsearch_indices_stats(
        config.DEFAULT_INDEX_NAME, service_name=foldered_name
    )
    assert indices_stats["_all"]["primaries"]["docs"]["count"] == 1
    doc = config.get_document(
        config.DEFAULT_INDEX_NAME, config.DEFAULT_INDEX_TYPE, 1, service_name=foldered_name
    )
    assert doc["_source"]["name"] == "Loren"

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.sanity
@pytest.mark.dcos_min_version("1.9")
def test_metrics():
    expected_metrics = [
        "node.data-0-node.fs.total.total_in_bytes",
        "node.data-0-node.jvm.mem.pools.old.peak_used_in_bytes",
        "node.data-0-node.jvm.threads.count",
    ]

    def expected_metrics_exist(emitted_metrics):
        # Elastic metrics are also dynamic and based on the service name# For eg:
        # elasticsearch.test__integration__elastic.node.data-0-node.thread_pool.listener.completed
        # To prevent this from breaking we drop the service name from the metric name
        # => data-0-node.thread_pool.listener.completed
        metric_names = [".".join(metric_name.split(".")[2:]) for metric_name in emitted_metrics]
        return sdk_metrics.check_metrics_presence(metric_names, expected_metrics)

    sdk_metrics.wait_for_service_metrics(
        config.PACKAGE_NAME,
        foldered_name,
        "data-0",
        "data-0-node",
        config.DEFAULT_TIMEOUT,
        expected_metrics_exist,
    )

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.sanity
def test_custom_yaml_base64():
    # Apply this custom YAML block as a base64-encoded string:

    # cluster:
    #   routing:
    #     allocation:
    #       node_initial_primaries_recoveries: 3

    # The default value is 4. We're just testing to make sure the YAML formatting survived intact and the setting
    # got updated in the config.
    base64_elasticsearch_yml = "Y2x1c3RlcjoNCiAgcm91dGluZzoNCiAgICBhbGxvY2F0aW9uOg0KICAgICAgbm9kZV9pbml0aWFsX3ByaW1hcmllc19yZWNvdmVyaWVzOiAz"

    sdk_service.update_configuration(
        config.PACKAGE_NAME,
        foldered_name,
        {"elasticsearch": {"custom_elasticsearch_yml": base64_elasticsearch_yml}},
        current_expected_task_count,
    )

    config.check_custom_elasticsearch_cluster_setting(service_name=foldered_name)


@pytest.mark.sanity
@pytest.mark.timeout(60 * 60)
def test_security_toggle_with_kibana(default_populated_index):
    # Verify that commercial APIs are disabled by default in Elasticsearch.
    config.verify_commercial_api_status(False, service_name=foldered_name)

    # Write some data with security disabled, enabled security, and afterwards verify that we can
    # still read what we wrote.
    document_security_disabled_id = 1
    document_security_disabled_fields = {"name": "Elasticsearch", "role": "search engine"}
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        document_security_disabled_id,
        document_security_disabled_fields,
        service_name=foldered_name,
    )

    # Verify that basic license is enabled by default.
    config.verify_xpack_license("basic", service_name=foldered_name)

    # Install Kibana.
    elasticsearch_url = "http://" + sdk_hosts.vip_host(foldered_name, "coordinator", 9200)
    sdk_install.install(
        config.KIBANA_PACKAGE_NAME,
        config.KIBANA_PACKAGE_NAME,
        0,
        {"kibana": {"elasticsearch_url": elasticsearch_url}},
        timeout_seconds=config.KIBANA_DEFAULT_TIMEOUT,
        wait_for_deployment=False,
        insert_strict_options=False,
    )

    # Verify that it works.
    config.check_kibana_adminrouter_integration("service/{}/".format(config.KIBANA_PACKAGE_NAME))

    # Uninstall it.
    sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)

    # Enable Elasticsearch security.
    sdk_service.update_configuration(
        config.PACKAGE_NAME,
        foldered_name,
        {
            "elasticsearch": {"xpack_security_enabled": True},
            "service": {"update_strategy": "parallel"},
        },
        current_expected_task_count,
    )

    # This should still be disabled.
    config.verify_commercial_api_status(False, service_name=foldered_name)

    # Start trial license.
    config.start_trial_license(service_name=foldered_name)

    # Set up passwords. Basic HTTP credentials will have to be used in HTTP requests to
    # Elasticsearch from now on.
    passwords = config.setup_passwords(foldered_name)

    # Verify trial license is working.
    config.verify_xpack_license(
        "trial",
        service_name=foldered_name,
        http_user=config.DEFAULT_ELASTICSEARCH_USER,
        http_password=passwords["elastic"],
    )
    config.verify_commercial_api_status(
        True,
        service_name=foldered_name,
        http_user=config.DEFAULT_ELASTICSEARCH_USER,
        http_password=passwords["elastic"],
    )

    # Write some data with security enabled, disable security, and afterwards verify that we can
    # still read what we wrote.
    document_security_enabled_id = 2
    document_security_enabled_fields = {"name": "X-Pack", "role": "commercial plugin"}
    config.create_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        document_security_enabled_id,
        document_security_enabled_fields,
        service_name=foldered_name,
        http_user=config.DEFAULT_ELASTICSEARCH_USER,
        http_password=passwords["elastic"],
    )

    # Install Kibana with security enabled.
    sdk_install.install(
        config.KIBANA_PACKAGE_NAME,
        config.KIBANA_PACKAGE_NAME,
        0,
        {
            "kibana": {
                "elasticsearch_url": elasticsearch_url,
                "elasticsearch_xpack_security_enabled": True,
                "user": config.DEFAULT_KIBANA_USER,
                "password": passwords["kibana"],
            }
        },
        timeout_seconds=config.KIBANA_DEFAULT_TIMEOUT,
        wait_for_deployment=False,
        insert_strict_options=False,
    )

    # Verify that it works. Notice that with security enabled, one has to access
    # /service/kibana/login instead of /service/kibana.
    config.check_kibana_adminrouter_integration(
        "service/{}/login".format(config.KIBANA_PACKAGE_NAME)
    )

    # Uninstall it.
    sdk_install.uninstall(config.KIBANA_PACKAGE_NAME, config.KIBANA_PACKAGE_NAME)

    # Disable Elastic security.
    sdk_service.update_configuration(
        config.PACKAGE_NAME,
        foldered_name,
        {
            "elasticsearch": {"xpack_security_enabled": False},
            "service": {"update_strategy": "parallel"},
        },
        current_expected_task_count,
    )

    # Verify we can read what was written before toggling security, without basic HTTP credentials.
    document_security_disabled = config.get_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        document_security_disabled_id,
        service_name=foldered_name,
    )
    assert (
        document_security_disabled["_source"]["name"] == document_security_disabled_fields["name"]
    )

    # Verify we can read what was written when security was enabled, without basic HTTP credentials.
    document_security_enabled = config.get_document(
        config.DEFAULT_INDEX_NAME,
        config.DEFAULT_INDEX_TYPE,
        document_security_enabled_id,
        service_name=foldered_name,
    )
    assert document_security_enabled["_source"]["name"] == document_security_enabled_fields["name"]

    # Set update_strategy back to serial.
    sdk_service.update_configuration(
        config.PACKAGE_NAME,
        foldered_name,
        {"service": {"update_strategy": "serial"}},
        current_expected_task_count,
    )


@pytest.mark.recovery
@pytest.mark.sanity
def test_losing_and_regaining_index_health(default_populated_index):
    config.check_elasticsearch_index_health(
        config.DEFAULT_INDEX_NAME, "green", service_name=foldered_name
    )
    sdk_cmd.kill_task_with_pattern(
        "data__.*Elasticsearch",
        "nobody",
        agent_host=sdk_tasks.get_service_tasks(foldered_name, "data-0-node")[0].host,
    )
    config.check_elasticsearch_index_health(
        config.DEFAULT_INDEX_NAME, "yellow", service_name=foldered_name
    )
    config.check_elasticsearch_index_health(
        config.DEFAULT_INDEX_NAME, "green", service_name=foldered_name
    )

    sdk_plan.wait_for_completed_deployment(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_master_reelection():
    initial_master = config.get_elasticsearch_master(service_name=foldered_name)
    sdk_cmd.kill_task_with_pattern(
        "master__.*Elasticsearch",
        "nobody",
        agent_host=sdk_tasks.get_service_tasks(foldered_name, initial_master)[0].host,
    )
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
    # Ideally, the pod will get placed on a different agent. This test will verify that the
    # remaining two masters find the replaced master at its new IP address. This requires a
    # reasonably low TTL for Java DNS lookups.
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod replace master-0")
    sdk_plan.wait_for_in_progress_recovery(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_data_node_replace():
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod replace data-0")
    sdk_plan.wait_for_in_progress_recovery(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_coordinator_node_replace():
    sdk_cmd.svc_cli(config.PACKAGE_NAME, foldered_name, "pod replace coordinator-0")
    sdk_plan.wait_for_in_progress_recovery(foldered_name)
    sdk_plan.wait_for_completed_recovery(foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
@pytest.mark.timeout(15 * 60)
def test_plugin_install_and_uninstall(default_populated_index):
    plugins = "analysis-icu"

    sdk_service.update_configuration(
        config.PACKAGE_NAME,
        foldered_name,
        {"elasticsearch": {"plugins": plugins}},
        current_expected_task_count,
    )

    config.check_elasticsearch_plugin_installed(plugins, service_name=foldered_name)

    sdk_service.update_configuration(
        config.PACKAGE_NAME,
        foldered_name,
        {"elasticsearch": {"plugins": ""}},
        current_expected_task_count,
    )

    config.check_elasticsearch_plugin_uninstalled(plugins, service_name=foldered_name)


@pytest.mark.recovery
@pytest.mark.sanity
def test_add_ingest_and_coordinator_nodes_does_not_restart_master_or_data_nodes():
    initial_master_task_ids = sdk_tasks.get_task_ids(foldered_name, "master")
    initial_data_task_ids = sdk_tasks.get_task_ids(foldered_name, "data")

    # Get service configuration.
    _, svc_config, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, foldered_name, "describe", parse_json=True
    )

    ingest_nodes_count = get_in(["ingest_nodes", "count"], svc_config)
    coordinator_nodes_count = get_in(["coordinator_nodes", "count"], svc_config)

    global current_expected_task_count

    sdk_service.update_configuration(
        config.PACKAGE_NAME,
        foldered_name,
        {
            "ingest_nodes": {"count": ingest_nodes_count + 1},
            "coordinator_nodes": {"count": coordinator_nodes_count + 1},
        },
        current_expected_task_count,
        # As of 2018-12-14, sdk_upgrade's `wait_for_deployment` has different behavior than
        # sdk_install's (which is what we wanted here), so don't use it. Check manually afterwards
        # with `sdk_tasks.check_running`.
        wait_for_deployment=False,
    )

    # Should be running 2 tasks more.
    current_expected_task_count += 2
    sdk_tasks.check_running(foldered_name, current_expected_task_count)
    # Master nodes should not restart.
    sdk_tasks.check_tasks_not_updated(foldered_name, "master", initial_master_task_ids)
    # Data nodes should not restart.
    sdk_tasks.check_tasks_not_updated(foldered_name, "data", initial_data_task_ids)


@pytest.mark.recovery
@pytest.mark.sanity
def test_adding_data_node_only_restarts_masters():
    initial_master_task_ids = sdk_tasks.get_task_ids(foldered_name, "master")
    initial_data_task_ids = sdk_tasks.get_task_ids(foldered_name, "data")
    initial_coordinator_task_ids = sdk_tasks.get_task_ids(foldered_name, "coordinator")

    # Get service configuration.
    _, svc_config, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, foldered_name, "describe", parse_json=True
    )

    data_nodes_count = get_in(["data_nodes", "count"], svc_config)

    global current_expected_task_count

    # Increase the data nodes count by 1.
    sdk_service.update_configuration(
        config.PACKAGE_NAME,
        foldered_name,
        {"data_nodes": {"count": data_nodes_count + 1}},
        current_expected_task_count,
        # As of 2018-12-14, sdk_upgrade's `wait_for_deployment` has different behavior than
        # sdk_install's (which is what we wanted here), so don't use it. Check manually afterwards
        # with `sdk_tasks.check_running`.
        wait_for_deployment=False,
    )

    sdk_plan.wait_for_kicked_off_deployment(foldered_name)
    sdk_plan.wait_for_completed_deployment(foldered_name)

    _, new_data_pod_info, _ = sdk_cmd.svc_cli(
        config.PACKAGE_NAME,
        foldered_name,
        "pod info data-{}".format(data_nodes_count),
        parse_json=True,
    )

    # Get task ID for new data node task.
    new_data_task_id = get_in([0, "info", "taskId", "value"], new_data_pod_info)

    # Should be running 1 task more.
    current_expected_task_count += 1
    sdk_tasks.check_running(foldered_name, current_expected_task_count)
    # Master nodes should restart.
    sdk_tasks.check_tasks_updated(foldered_name, "master", initial_master_task_ids)
    # Data node tasks should be the initial ones plus the new one.
    sdk_tasks.check_tasks_not_updated(
        foldered_name, "data", initial_data_task_ids + [new_data_task_id]
    )
    # Coordinator tasks should not restart.
    sdk_tasks.check_tasks_not_updated(foldered_name, "coordinator", initial_coordinator_task_ids)


# NOTE: this test should be at the end of this module.
# TODO(mpereira): it is safe to remove this test after the 6.x release.
@pytest.mark.sanity
@pytest.mark.timeout(20 * 60)
def test_xpack_update_matrix():
    # Since this test uninstalls the Elastic service that is shared between all previous tests,
    # reset the number of expected tasks to the default value. This is checked before all tests
    # by the `pre_test_setup` fixture.
    global current_expected_task_count
    current_expected_task_count = config.DEFAULT_TASK_COUNT

    log.info("Updating X-Pack from 'enabled' to 'enabled'")
    config.test_xpack_enabled_update(foldered_name, True, True)

    log.info("Updating X-Pack from 'enabled' to 'disabled'")
    config.test_xpack_enabled_update(foldered_name, True, False)

    log.info("Updating X-Pack from 'disabled' to 'enabled'")
    config.test_xpack_enabled_update(foldered_name, False, True)

    log.info("Updating X-Pack from 'disabled' to 'disabled'")
    config.test_xpack_enabled_update(foldered_name, False, False)


# NOTE: this test should be at the end of this module.
# TODO(mpereira): change this to xpack_security_enabled to xpack_security_enabled after the 6.x
# release.
@pytest.mark.sanity
@pytest.mark.timeout(60 * 60)
def test_xpack_security_enabled_update_matrix():
    # Since this test uninstalls the Elastic service that is shared between all previous tests,
    # reset the number of expected tasks to the default value. This is checked before all tests
    # by the `pre_test_setup` fixture.
    global current_expected_task_count
    current_expected_task_count = config.DEFAULT_TASK_COUNT

    log.info("Updating from X-Pack 'enabled' to X-Pack security 'enabled'")
    config.test_update_from_xpack_enabled_to_xpack_security_enabled(foldered_name, True, True)

    log.info("Updating from X-Pack 'enabled' to X-Pack security 'disabled'")
    config.test_update_from_xpack_enabled_to_xpack_security_enabled(foldered_name, True, False)

    log.info("Updating from X-Pack 'disabled' to X-Pack security 'enabled'")
    config.test_update_from_xpack_enabled_to_xpack_security_enabled(foldered_name, False, True)

    log.info("Updating from X-Pack 'disabled' to X-Pack security 'disabled'")
    config.test_update_from_xpack_enabled_to_xpack_security_enabled(foldered_name, False, False)
