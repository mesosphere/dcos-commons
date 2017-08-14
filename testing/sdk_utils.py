import logging

import dcos
import shakedown
import pytest

log = logging.getLogger(__name__)


def list_reserved_resources():
    '''Displays the currently reserved resources on all agents via state.json;
       Currently for INFINITY-1881 where we believe uninstall may not be
       always doing its job correctly.'''
    state_json_slaveinfo = dcos.mesos.DCOSClient().get_state_summary()['slaves']

    for slave in state_json_slaveinfo:
        reserved_resources = slave['reserved_resources']
        if reserved_resources == {}:
            continue
        msg = "on slaveid=%s hostname=%s reserved resources: %s"
        log.info(msg % (slave['id'], slave['hostname'], reserved_resources))


def get_foldered_name(service_name):
    # DCOS 1.9 & earlier don't support "foldered", service names aka marathon
    # group names
    if shakedown.dcos_version_less_than("1.10"):
        return service_name
    return "/test/integration/" + service_name


def get_zk_path(service_name):
    # DCOS 1.9 & earlier don't support "foldered", service names aka marathon
    # group names
    if shakedown.dcos_version_less_than("1.10"):
        return service_name
    return "test__integration__" + service_name


# WARNING: Any file that uses these must also "import shakedown" in the same file.
dcos_1_9_or_higher = pytest.mark.skipif(
    'shakedown.dcos_version_less_than("1.9")',
    reason="Feature only supported in DC/OS 1.9 and up")
dcos_1_10_or_higher = pytest.mark.skipif(
    'shakedown.dcos_version_less_than("1.10")',
    reason="Feature only supported in DC/OS 1.10 and up")

