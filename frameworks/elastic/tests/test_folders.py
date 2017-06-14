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
    utils.gc_frameworks()


def teardown_module(module):
    uninstall_foldered()


@pytest.mark.sanity
@pytest.mark.smoke
def test_install_foldered():
    install.install(
        PACKAGE_NAME,
        DEFAULT_TASK_COUNT,
        service_name=FOLDERED_SERVICE_NAME,
        additional_options={"service": { "name": FOLDERED_SERVICE_NAME } })
    plan.wait_for_completed_deployment(FOLDERED_SERVICE_NAME)

    # check that we can reach the scheduler via admin router, and that returned endpoints are sanitized:
    for nodetype in ('coordinator', 'data', 'ingest', 'master'):
        endpoints = json.loads(cmd.run_cli('elastic --name={} endpoints {}'.format(FOLDERED_SERVICE_NAME, nodetype)))
        assert endpoints['dns'][0].startswith('{}-0-node.pathtoelastic.autoip.dcos.thisdcos.directory:'.format(nodetype))
        assert endpoints['vips'][0].startswith('{}.pathtoelastic.l4lb.thisdcos.directory:'.format(nodetype))
