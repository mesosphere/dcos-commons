import pytest

import sdk_install as install
import sdk_plan as plan
from tests.test_utils import (
    DEFAULT_BROKER_COUNT,
    PACKAGE_NAME,
    SERVICE_NAME,
    broker_count_check,
    service_cli
)

FOLDERED_SERVICE_NAME = "/path/to/" + SERVICE_NAME


def uninstall_foldered():
    install.uninstall(FOLDERED_SERVICE_NAME, package_name=PACKAGE_NAME)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    uninstall_foldered()


def teardown_module(module):
    uninstall_foldered()


@pytest.mark.sanity
@pytest.mark.smoke
def test_install_foldered():
    install.install(
        PACKAGE_NAME,
        DEFAULT_BROKER_COUNT,
        service_name=FOLDERED_SERVICE_NAME,
        additional_options={"service": { "name": FOLDERED_SERVICE_NAME } })
    plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME)

    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    zkurl = service_cli('endpoints zookeeper', get_json=False, service_name=FOLDERED_SERVICE_NAME)
    assert zkurl.strip() == 'master.mesos:2181/dcos-service-path__to__kafka'
    endpoints = service_cli('endpoints broker', service_name=FOLDERED_SERVICE_NAME)
    assert endpoints['dns'][0].startswith('kafka-0-broker.pathtokafka.autoip.dcos.thisdcos.directory:')
    assert endpoints['vips'][0] == 'broker.pathtokafka.l4lb.thisdcos.directory:9092'
