import pytest
import shakedown

import sdk_install as install
import sdk_utils

PACKAGE_NAME = "kibana"
DEFAULT_TASK_COUNT = 1
WAIT_TIME_IN_SECONDS = 15 * 60


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    shakedown.install_package(PACKAGE_NAME)
    shakedown.deployment_wait(timeout=WAIT_TIME_IN_SECONDS)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
def test_xpack():
    wait_for_kibana_success()


def kibana_endpoint_success_predicate():
    cmd = "curl -I -s http://web.kibana.marathon.l4lb.thisdcos.directory:80/"
    exit_status, result = shakedown.run_command_on_master(cmd)
    sdk_utils.out("Looking for success status from Kibana home: {}".format(result))
    return result and "HTTP/1.1 200" in result


def wait_for_kibana_success():
    return shakedown.wait_for(lambda: kibana_endpoint_success_predicate(), timeout_seconds=WAIT_TIME_IN_SECONDS)

