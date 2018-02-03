import pytest
import retrying
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_metrics
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_security
import sdk_utils
import sdk_repository
import shakedown
from tests import config, test_utils


ZK_PACKAGE = "kafka-zookeeper"
ZK_SERVICE_NAME = "kafka-zookeeper"


# NOTE: This can be removed after we publish a zookeeper that
# has at least through sha a0d96b28769e4cb871b3e2424f4c6b889f5a06dd
@pytest.fixture(scope='module', autouse=True)
def install_zookeeper_stub():
    try:
        zk_url = "https://universe-converter.mesosphere.com/transform?url=https://infinity-artifacts.s3.amazonaws.com/permanent/kafka-zookeeper/assets/sha-a0d96b28769e4cb871b3e2424f4c6b889f5a06dd/stub-universe-kafka-zookeeper.json"

        stub_urls = sdk_repository.add_stub_universe_urls([zk_url, ])
        yield
    finally:
        sdk_repository.remove_universe_repos(stub_urls)


@pytest.fixture(scope='module', autouse=True)
def configure_zookeeper(configure_security, install_zookeeper_stub):
    service_options = {
        "service": {
            "virtual_network_enabled": True
        }
    }

    zk_account = "test-zookeeper-service-account"
    zk_secret = "test-zookeeper-secret"

    try:
        sdk_install.uninstall(ZK_PACKAGE, ZK_SERVICE_NAME)
        if sdk_utils.is_strict_mode():
            service_options = sdk_install.merge_dictionaries({
                'service': {
                    'service_account': zk_account,
                    'service_account_secret': zk_secret,
                }
            }, service_options)

            sdk_security.setup_security(ZK_SERVICE_NAME, zk_account, zk_secret)

        sdk_install.install(
            ZK_PACKAGE,
            ZK_SERVICE_NAME,
            6,
            additional_options=service_options,
            timeout_seconds=30 * 60,
            insert_strict_options=False)

        yield
    finally:
        sdk_install.uninstall(ZK_PACKAGE, ZK_SERVICE_NAME)
        if sdk_utils.is_strict_mode():
            sdk_security.delete_service_account(
                service_account_name=zk_account, service_account_secret=zk_secret)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_zookeeper):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        zookeeper_framework_host = "{}.autoip.dcos.thisdcos.directory:1140".format(ZK_SERVICE_NAME)
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options={
                "kafka": {
                    "kafka_zookeeper_uri": "zookeeper-0-server.{host},zookeeper-0-server.{host},zookeeper-0-server.{host}".format(host=zookeeper_framework_host)
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
def test_zookeeper_reresolution():

    def restart_zookeeper_node(id: int):
        sdk_cmd.svc_cli(ZK_PACKAGE, ZK_SERVICE_NAME, "pod restart zookeeper-{}".format(id))

        sdk_plan.wait_for_kicked_off_recovery(ZK_SERVICE_NAME)
        sdk_plan.wait_for_completed_recovery(ZK_SERVICE_NAME)

    # Restart each zookeeper node, so that each one receives a new IP address
    # (it's on a virtual network). This will force Kafka to re-resolve ZK nodes.
    for id in range(0, 3):
        restart_zookeeper_node(id)

    # Now, verify that Kafka remains happy
    def check_broker(id: int):
        rc, stdout, stderr = sdk_cmd.run_raw_cli("task log kafka-{}-broker --lines 15".format(id))

        if rc or not stdout:
            raise Exception("No task logs for kafka-{}-broker".format(id))

        assert "java.net.NoRouteToHostException: No route to host" not in stdout

    for id in range(0, 3):
        check_broker(id)
