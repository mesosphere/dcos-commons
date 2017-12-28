'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_utils IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import functools
import logging
import operator

import dcos
import shakedown
import pytest
import os
import os.path

log = logging.getLogger(__name__)
# The index to use when constructing the test log directory.
test_index = -1


def get_package_name(default: str) -> str:
    return os.environ.get("INTEGRATION_TEST__PACKAGE_NAME") or default


def get_service_name(default: str) -> str:
    return os.environ.get("INTEGRATION_TEST__SERVICE_NAME") or default


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


def set_test_index(index):
    '''Assigns the index to use for a test within a given test suite.
    Should start at 1 for the first test in the suite.'''
    global test_index
    test_index = index


def get_test_suite_name(pytest_node):
    '''Returns the test suite name to use for a given test.'''
    # frameworks/template/tests/test_sanity.py => test_sanity_py
    # tests/test_sanity.py => test_sanity_py
    return os.path.basename(pytest_node.parent.name).replace('.','_')


def get_test_suite_log_directory(pytest_node):
    '''Returns the parent directory for the logs across a suite of tests.
    For individual tests within this directory, see get_test_log_directory().'''
    return os.path.join('logs', get_test_suite_name(pytest_node))


def get_test_log_directory(pytest_node):
    '''Returns the directory for the logs of a single test.
    For the parent test suite directory, see get_test_suite_log_directory().'''
    # full item.listchain() is e.g.:
    # - ['build', 'frameworks/template/tests/test_sanity.py', 'test_install']
    # - ['build', 'tests/test_sanity.py', 'test_install']
    # we want to turn both cases into: 'logs/test_sanity_py/test_install'
    global test_index
    if test_index > 0:
        # test_index is defined: get name like "05__test_placement_rules"
        test_name = '{:02d}__{}'.format(test_index, pytest_node.name)
    else:
        # test_index is not defined: fall back to just "test_placement_rules"
        test_name = pytest_node.name
    return os.path.join(get_test_suite_log_directory(pytest_node), test_name)


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
    return not (os.environ.get('DCOS_ENTERPRISE', 'true').lower() == 'true')


def is_strict_mode():
    '''Determine if the tests are being run on a strict mode cluster.'''
    return os.environ.get('SECURITY', '') == 'strict'


dcos_ee_only = pytest.mark.skipif(
    is_open_dcos(),
    reason="Feature only supported in DC/OS EE.")


# Pretty much https://github.com/pytoolz/toolz/blob/a8cd0adb5f12ec5b9541d6c2ef5a23072e1b11a3/toolz/dicttoolz.py#L279
def get_in(keys, coll, default=None):
    """ Reaches into nested associative data structures. Returns the value for path ``keys``.

    If the path doesn't exist returns ``default``.

    >>> transaction = {'name': 'Alice',
    ...                'purchase': {'items': ['Apple', 'Orange'],
    ...                             'costs': [0.50, 1.25]},
    ...                'credit card': '5555-1234-1234-1234'}
    >>> get_in(['purchase', 'items', 0], transaction)
    'Apple'
    >>> get_in(['name'], transaction)
    'Alice'
    >>> get_in(['purchase', 'total'], transaction)
    >>> get_in(['purchase', 'items', 'apple'], transaction)
    >>> get_in(['purchase', 'items', 10], transaction)
    >>> get_in(['purchase', 'total'], transaction, 0)
    0
    """
    try:
        return functools.reduce(operator.getitem, keys, coll)
    except (KeyError, IndexError, TypeError):
        return default
