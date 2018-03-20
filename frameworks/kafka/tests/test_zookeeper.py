import pytest
import retrying

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_security
import sdk_utils

from tests import config, test_utils


@pytest.fixture(scope='module', autouse=True)
def zookeeper_server(configure_security):
    service_options = {
        "service": {
            "name": config.ZOOKEEPER_SERVICE_NAME,
            "virtual_network_enabled": True
        }
    }

    zk_account = "test-zookeeper-service-account"
    zk_secret = "test-zookeeper-secret"

    try:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        if sdk_utils.is_strict_mode():
            service_options = sdk_install.merge_dictionaries({
                'service': {
                    'service_account': zk_account,
                    'service_account_secret': zk_secret,
                }
            }, service_options)

            sdk_security.setup_security(config.ZOOKEEPER_SERVICE_NAME, zk_account, zk_secret)

        sdk_install.install(
            config.ZOOKEEPER_PACKAGE_NAME,
            config.ZOOKEEPER_SERVICE_NAME,
            config.ZOOKEEPER_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
            insert_strict_options=False)

        yield {**service_options, **{"package_name": config.ZOOKEEPER_PACKAGE_NAME}}

    finally:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        if sdk_utils.is_strict_mode():
            sdk_security.delete_service_account(
                service_account_name=zk_account, service_account_secret=zk_secret)


@pytest.fixture(scope='module', autouse=True)
def kafka_server(zookeeper_server):
    try:

        # Get the zookeeper DNS values
        zookeeper_dns = sdk_cmd.svc_cli(zookeeper_server["package_name"],
                                        zookeeper_server["service"]["name"],
                                        "endpoint clientport", json=True)["dns"]

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options={
                "kafka": {
                    "kafka_zookeeper_uri": ",".join(zookeeper_dns)
                }
            })

        # wait for brokers to finish registering before starting tests
        test_utils.broker_count_check(config.DEFAULT_BROKER_COUNT,
                                      service_name=config.SERVICE_NAME)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.zookeeper
def test_zookeeper_reresolution(kafka_server):

    def restart_zookeeper_node(id: int):
        sdk_cmd.svc_cli(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME,
                        "pod restart zookeeper-{}".format(id))

        sdk_plan.wait_for_kicked_off_recovery(config.ZOOKEEPER_SERVICE_NAME)
        sdk_plan.wait_for_completed_recovery(config.ZOOKEEPER_SERVICE_NAME)

    # Restart each zookeeper node, so that each one receives a new IP address
    # (it's on a virtual network). This will force Kafka to re-resolve ZK nodes.
    for id in range(0, int(config.ZOOKEEPER_TASK_COUNT / 2)):
        restart_zookeeper_node(id)

    # Now, verify that Kafka remains happy
    @retrying.retry(
        wait_fixed=1000,
        stop_max_delay=2*60*1000)
    def check_broker(id: int):
        rc, stdout, _ = sdk_cmd.run_raw_cli("task log kafka-{}-broker --lines 100".format(id))

        if rc or not stdout:
            raise Exception("No task logs for kafka-{}-broker".format(id))

        expired_index = stdout.rfind("zookeeper state changed (Expired) (org.I0Itec.zkclient.ZkClient)")
        exception_index = stdout.rfind("java.net.NoRouteToHostException: No route to host")

        success_index = stdout.rfind("zookeeper state changed (SyncConnected) (org.I0Itec.zkclient.ZkClient)")

        assert max(expired_index, exception_index) > -1
        assert max(expired_index, exception_index) < success_index

    for id in range(0, config.DEFAULT_BROKER_COUNT):
        check_broker(id)
