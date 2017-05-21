import pytest
import shakedown

import sdk_install as install
import sdk_tasks as tasks
import sdk_utils as utils
from tests.config import wait_for_expected_nodes_to_exist, DEFAULT_TASK_COUNT

PACKAGE_NAME = "kibana"
ELASTIC_PACKAGE_NAME = "elastic"
WAIT_TIME_IN_SECONDS = 15 * 60


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    install.uninstall(ELASTIC_PACKAGE_NAME)
    utils.gc_frameworks()


@pytest.mark.sanity
@pytest.mark.smoke
def test_kibana_installation_no_xpack():
    options = {}
    path = "/"
    test_kibana(options, path)


@pytest.mark.sanity
def test_kibana_installation_with_xpack():
    # Note that this test may take 20-30 minutes to run.

    # Kibana needs to be able to connect to an elasticsearch w/x-pack cluster in order to return success on /login
    # Otherwise it will generate infinite 302 redirects.
    options = {
        "elasticsearch": {
            "xpack_enabled": True
        }
    }
    shakedown.install_package(ELASTIC_PACKAGE_NAME, options_json=options)
    tasks.check_running(ELASTIC_PACKAGE_NAME, DEFAULT_TASK_COUNT)
    wait_for_expected_nodes_to_exist()

    options = {
        "kibana": {
            "xpack_enabled": True
        }
    }
    path = "/login"
    # installing Kibana w/x-pack can take 15 minutes
    test_kibana(options, path)
    install.uninstall(ELASTIC_PACKAGE_NAME)


def test_kibana(options, path):
    shakedown.install_package(PACKAGE_NAME, options_json=options)
    shakedown.deployment_wait(timeout=WAIT_TIME_IN_SECONDS, app_id="/{}".format(PACKAGE_NAME))
    shakedown.wait_for(lambda: kibana_endpoint_success_predicate(path), timeout_seconds=WAIT_TIME_IN_SECONDS)
    install.uninstall(PACKAGE_NAME)


def kibana_endpoint_success_predicate(path):
    cmd = "curl -I http://web.kibana.marathon.l4lb.thisdcos.directory:80{}".format(path)
    exit_status, result = shakedown.run_command_on_master(cmd)
    utils.out("Waiting for success status from Kibana home: {}".format(result))
    return result and "HTTP/1.1 200" in result
