import json
import pytest

from tests.config import *
import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan

FOLDERED_SERVICE_NAME = "/path/to/" + PACKAGE_NAME


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
    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(
        PACKAGE_NAME,
        DEFAULT_TASK_COUNT,
        service_name=FOLDERED_SERVICE_NAME,
        additional_options={"service": { "name": FOLDERED_SERVICE_NAME } },
        check_suppression=False)
    plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME)

    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    endpoints = json.loads(cmd.run_cli('cassandra --name={} endpoints node'.format(FOLDERED_SERVICE_NAME)))
    assert endpoints['dns'][0] == 'node-0-server.pathtocassandra.autoip.dcos.thisdcos.directory:9042'
    assert endpoints['vips'][0] == 'node.pathtocassandra.l4lb.thisdcos.directory:9042'
