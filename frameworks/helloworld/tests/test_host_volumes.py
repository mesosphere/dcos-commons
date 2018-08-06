import logging
import retrying
import pytest

import sdk_cmd
import sdk_install
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {
            "service": {
                "yaml": "host-volume"
            }
        }

        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 2, additional_options=options)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.hostvolume
@pytest.mark.sanity
@pytest.mark.smoke
def test_check_host_volume_mounts():
    '''world has no docker image defined, hello does. Check to make sure the host volume is mounted in the container'''
    assert "host-volume-etc" in search_for_host_volume("hello-0-server", "bash -c 'mount'", "host-volume-etc")
    assert "host-volume-etc" in search_for_host_volume("world-0-server", "bash -c 'mount'", "host-volume-etc")
    assert "host-volume-var" in search_for_host_volume("world-0-server", "bash -c 'mount'", "host-volume-var")


@retrying.retry(wait_fixed=2000, stop_max_delay=5 * 60 * 1000)
def search_for_host_volume(task_name, command, mount_name):
    _, output, _ = sdk_cmd.service_task_exec(config.SERVICE_NAME, task_name, command)
    lines = [line.strip() for line in output.split('\n')]
    log.info('Looking for %s...', mount_name)
    for line in lines:
        if mount_name in line:
            return line
    raise Exception("Failed to read host volume mountpoint from {} with command '{}'".format(task_name, command))
