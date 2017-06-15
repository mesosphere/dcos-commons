import pytest

import sdk_install as install
import sdk_tasks as tasks
import sdk_utils as utils
import shakedown


from tests.test_utils import (
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_BROKER_COUNT,
    DEFAULT_PLAN_NAME,
    service_cli
)


def setup_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)
    utils.gc_frameworks()

    install.install(
        PACKAGE_NAME,
        DEFAULT_BROKER_COUNT,
        service_name=SERVICE_NAME,
        additional_options = {'service':{'virtual_network':True}})


# gc_frameworks to make sure after each uninstall
def teardown_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)


# --------- Placement -------------


@pytest.mark.smoke
@pytest.mark.sanity
@pytest.mark.speedy
@pytest.mark.skip("https://jira.mesosphere.com/browse/INFINITY-1656 LIBPROCESS_IP will be 0.0.0.0")
def test_cni_deployment():
    # double check
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)
    plan.wait_for_completed_deployment(SERVICE_NAME)

    # test endpoints output
    def fun():
        ret = service_cli('endpoints {}'.format(DEFAULT_TASK_NAME))
        if len(ret['address']) == DEFAULT_BROKER_COUNT:
            return ret
        return False
    endpoints = shakedown.wait_for(fun, noisy=True, timeout_seconds=5 * 60)
    assert len(endpoints['address']) == DEFAULT_BROKER_COUNT
    assert len(endpoints['dns']) == DEFAULT_BROKER_COUNT
    for dns_endpoint in endpoints['dns']:
        assert "autoip.dcos.thisdcos.directory" in dns_endpoint

    zookeeper = service_cli('endpoints zookeeper', get_json=False)
    assert zookeeper.rstrip() == 'master.mesos:2181/dcos-service-{}'.format(PACKAGE_NAME)
