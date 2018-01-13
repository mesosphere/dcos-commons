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
import sdk_utils
import shakedown
from tests import config, test_utils


ZK_PACKAGE = "beta-kafka-zookeeper"
ZK_SERVICE_NAME = "kafka-zookeeper"


@pytest.fixture(scope='module', autouse=True)
def configure_zookeeper(configure_security):
    try:
        sdk_install.uninstall(ZK_PACKAGE, ZK_SERVICE_NAME)

        sdk_install.install(package_name=ZK_PACKAGE,
                        expected_running_tasks=6,
                        service_name=ZK_SERVICE_NAME,
                        wait_for_deployment=True,
                        additional_options={
                            "service": {
                                "virtual_network_enabled": True
                            }
                        })

        yield
    finally:
        sdk_install.uninstall(ZK_PACKAGE, ZK_SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_zookeeper):
    try:
        foldered_name = sdk_utils.get_foldered_name(config.SERVICE_NAME)
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        zookeeper_framework_host = "{}.data-servicesconfluent-zookeeper.autoip.dcos.thisdcos.directory:1140".format(ZK_SERVICE_NAME)

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
                                      service_name=foldered_name)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.mark.sanity
@pytest.mark.zookeeper
@pytest.mark.ben
def test_zookeeper_reresolution():
    pass
