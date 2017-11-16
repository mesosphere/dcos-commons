import logging
import pytest
import sdk_install
import sdk_tasks
from tests import config
from tests import nodetool

log = logging.getLogger(__name__)

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_rack():
    sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 3)

    # dcos task exec node-0-server bash -c 'JAVA_HOME=jre1.8.0_144 apache-cassandra-3.0.14/bin/nodetool status'
    raw_status = nodetool.cmd('node-0', 'status')
    log.info("raw_status: {}".format(raw_status))
    stdout = raw_status[1]
    log.info("stdout: {}".format(stdout))

    node = nodetool.parse_status(stdout)[0]
    log.info("node: {}".format(node))

    assert node.get_rack() != 'rack1'
    assert 'us-west' in node.get_rack()

