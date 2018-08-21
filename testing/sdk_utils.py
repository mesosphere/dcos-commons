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


def get_package_name(default: str) -> str:
    return os.environ.get("INTEGRATION_TEST__PACKAGE_NAME") or default


def get_service_name(default: str) -> str:
    return os.environ.get("INTEGRATION_TEST__SERVICE_NAME") or default


def list_reserved_resources(service_name: str) -> bool:
    """
    Logs all reserved resources and returns a true value if there are some for this service's role.

    The reservations are retrieved from state.json.
    This is for debugging DCOS-40654.
    """
    service_role = service_name.strip("/").replace("/", "__") + "-role"
    state_json_slave_info = dcos.mesos.DCOSClient().get_state_summary()['slaves']
    reserved_resources_messages = []
    found_for_this_service = False
    msg = 'on slaveid=%s hostname=%s reserved resources: %s'
    for slave in state_json_slave_info:
        reserved_resources = slave['reserved_resources']
        if not reserved_resources:
            continue
        reserved_resources_messages.append(msg % (slave['id'], slave['hostname'], reserved_resources))
        if service_role in reserved_resources:
            found_for_this_service = True
    if reserved_resources_messages:
        log.info('Found following reserved resources post uninstall.')
        for reserved_resources_message in reserved_resources_messages:
            log.info(reserved_resources_message)
    return found_for_this_service


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


def dcos_version_at_least(version):
    return not dcos_version_less_than(version)


def check_dcos_min_version_mark(item: pytest.Item):
    '''Enforces the dcos_min_version pytest annotation, which should be used like this:

    @pytest.mark.dcos_min_version('1.10')
    def your_test_here(): ...

    In order for this annotation to take effect, this function must be called by a pytest_runtest_setup() hook.
    '''
    min_version_mark = item.get_marker('dcos_min_version')
    if min_version_mark:
        min_version = min_version_mark.args[0]
        message = 'Feature only supported in DC/OS {} and up'.format(min_version)
        if 'reason' in min_version_mark.kwargs:
            message += ': {}'.format(min_version_mark.kwargs['reason'])
        if dcos_version_less_than(min_version):
            pytest.skip(message)


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


def sort(coll):
    """ Sorts a collection and returns it. """
    coll.sort()
    return coll


def invert_dict(d: dict) -> dict:
    """ Returns a dictionary with its values being its keys and vice-versa. """
    return dict((v, k) for k, v in d.items())
