import pytest

from tests.config import *
import sdk_install as install
import sdk_tasks as tasks
import sdk_utils as utils


OVERLAY_OPTIONS = {'service':{'virtual_network':True}}

def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False, additional_options=OVERLAY_OPTIONS)


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.overlay
def test_service_health():
    check_dcos_service_health()
    # node-0-server
    # node-1-server
    # node-2-server


