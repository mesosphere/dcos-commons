'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_utils IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import functools
import logging

import dcos
import shakedown
import pytest
import os

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
        msg = 'on slaveid=%s hostname=%s reserved resources: %s'
        log.info(msg % (slave['id'], slave['hostname'], reserved_resources))


def get_foldered_name(service_name):
    # DCOS 1.9 & earlier don't support "foldered", service names aka marathon
    # group names
    if dcos_version_less_than('1.10'):
        return service_name
    return '/test/integration/' + service_name


def get_zk_path(service_name):
    # Foldered services have slashes removed: '/test/integration/foo' => 'test__integration__foo'
    return 'dcos-service-{}'.format(service_name.lstrip('/').replace('/', '__'))


@functools.lru_cache()
def dcos_version_less_than(version):
    return shakedown.dcos_version_less_than(version)


def is_test_failure(pytest_request):
    '''Determine if the test run failed using the request object from pytest.
    The reports being evaluated are set in conftest.py:pytest_runtest_makereport()
    https://docs.pytest.org/en/latest/builtin.html#_pytest.fixtures.FixtureRequest
    '''
    for report in ('rep_setup', 'rep_call', 'rep_teardown'):
        if not hasattr(pytest_request.node, report):
            continue
        if not getattr(pytest_request.node, report).failed:
            continue
        return True
    return False


def is_open_dcos():
    '''Determine if the tests are being run against open DC/OS. This is presently done by
    checking the envvar DCOS_ENTERPRISE.'''
    return os.environ.get('DCOS_ENTERPRISE', 'true').lower() != 'true'


dcos_ee_only = pytest.mark.skipif(
    'sdk_utils.is_open_dcos',
    reason="Feature only supported in DC/OS EE.")


# WARNING: Any file that uses these must also "import shakedown" in the same file.
dcos_1_9_or_higher = pytest.mark.skipif(
    'sdk_utils.dcos_version_less_than("1.9")',
    reason="Feature only supported in DC/OS 1.9 and up")
dcos_1_10_or_higher = pytest.mark.skipif(
    'sdk_utils.dcos_version_less_than("1.10")',
    reason="Feature only supported in DC/OS 1.10 and up")
