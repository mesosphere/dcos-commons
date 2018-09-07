import pytest
import retrying

import sdk_cmd
import sdk_install
import sdk_networks
import sdk_plan
import sdk_security
import sdk_utils

from tests import config, test_utils


pytestmark = pytest.mark.skip(reason="INFINITY-3363: Skipping test until it is better implemented")


@pytest.fixture(scope="module", autouse=True)
def zookeeper_service(configure_security):
    service_options = sdk_utils.merge_dictionaries(
        sdk_networks.ENABLE_VIRTUAL_NETWORKS_OPTIONS,
        {"service": {"name": config.ZOOKEEPER_SERVICE_NAME}},
    )

    zk_account = "test-zookeeper-service-account"
    zk_secret = "test-zookeeper-secret"

    try:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        if sdk_utils.is_strict_mode():
            service_options = sdk_utils.merge_dictionaries(
                {"service": {"service_account": zk_account, "service_account_secret": zk_secret}},
                service_options,
            )

            service_account_info = sdk_security.setup_security(
                config.ZOOKEEPER_SERVICE_NAME,
                linux_user="nobody",
                service_account=zk_account,
                service_account_secret=zk_secret,
            )

        sdk_install.install(
            config.ZOOKEEPER_PACKAGE_NAME,
            config.ZOOKEEPER_SERVICE_NAME,
            config.ZOOKEEPER_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
            insert_strict_options=False,
        )

        yield {**service_options, **{"package_name": config.ZOOKEEPER_PACKAGE_NAME}}

    finally:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        sdk_security.cleanup_security(config.ZOOKEEPER_SERVICE_NAME, service_account_info)


@pytest.fixture(scope="module", autouse=True)
def kafka_server(zookeeper_service):
    try:

        # Get the zookeeper DNS values
        zookeeper_dns = sdk_networks.get_endpoint(
            zookeeper_service["package_name"], zookeeper_service["service"]["name"], "clientport"
        )["dns"]

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options={"kafka": {"kafka_zookeeper_uri": ",".join(zookeeper_dns)}},
        )

        # wait for brokers to finish registering before starting tests
        test_utils.broker_count_check(config.DEFAULT_BROKER_COUNT, service_name=config.SERVICE_NAME)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.zookeeper
def test_zookeeper_reresolution(kafka_server):

    # First get the last logs lines for the kafka brokers
    broker_log_line = []

    for id in range(0, config.DEFAULT_BROKER_COUNT):
        rc, stdout, _ = sdk_cmd.run_cli("task log kafka-{}-broker --lines 1".format(id))

        if rc or not stdout:
            raise Exception("No task logs for kafka-{}-broker".format(id))

        broker_log_line.append(stdout)

    def restart_zookeeper_node(id: int):
        sdk_cmd.svc_cli(
            config.ZOOKEEPER_PACKAGE_NAME,
            config.ZOOKEEPER_SERVICE_NAME,
            "pod restart zookeeper-{}".format(id),
        )

        sdk_plan.wait_for_kicked_off_recovery(config.ZOOKEEPER_SERVICE_NAME)
        sdk_plan.wait_for_completed_recovery(config.ZOOKEEPER_SERVICE_NAME)

    # Restart each zookeeper node, so that each one receives a new IP address
    # (it's on a virtual network). This will force Kafka to re-resolve ZK nodes.
    for id in range(0, int(config.ZOOKEEPER_TASK_COUNT / 2)):
        restart_zookeeper_node(id)

    # Now, verify that Kafka remains happy
    @retrying.retry(wait_fixed=1000, stop_max_attempt_number=3)
    def check_broker(id: int):
        rc, stdout, _ = sdk_cmd.run_cli(
            "task log kafka-{}-broker --lines 1000".format(id), print_output=False
        )

        if rc or not stdout:
            raise Exception("No task logs for kafka-{}-broker".format(id))

        last_log_index = stdout.rfind(broker_log_line[id])
        success_index = stdout.rfind(
            "zookeeper state changed (SyncConnected) (org.I0Itec.zkclient.ZkClient)"
        )

        assert last_log_index > -1 and last_log_index < success_index, "{}:{} STDOUT: {}".format(
            last_log_index, success_index, stdout
        )

    for id in range(0, config.DEFAULT_BROKER_COUNT):
        check_broker(id)
