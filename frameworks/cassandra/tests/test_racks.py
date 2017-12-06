import logging
import pytest
import shakedown
import sdk_install
import sdk_tasks
import sdk_utils

from tests import config
from tests import nodetool

log = logging.getLogger(__name__)

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.get_foldered_service_name())



@pytest.mark.dcos_min_version('1.11')
@pytest.mark.sanity
def test_rack():
    sdk_install.install(
        config.PACKAGE_NAME,
        config.get_foldered_service_name(),
        3,
        additional_options={
            "service": {
                "name": config.get_foldered_service_name()
            },
            "nodes": {
                "placement_constraint": "@zone:GROUP_BY:1"
            }
        })

    # dcos task exec node-0-server bash -c 'JAVA_HOME=jre1.8.0_144 apache-cassandra-3.0.14/bin/nodetool status'
    raw_status = nodetool.cmd('node-0', 'status')
    log.info("raw_status: {}".format(raw_status))
    stdout = raw_status[1]
    log.info("stdout: {}".format(stdout))

    node = nodetool.parse_status(stdout)[0]
    log.info("node: {}".format(node))

    assert node.get_rack() != 'rack1'
    assert 'us-west' in node.get_rack()

