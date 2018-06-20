'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_utils IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import functools
import logging
import operator
import random
import string

import dcos
import shakedown
import pytest
import os
import os.path

log = logging.getLogger(__name__)


def is_env_var_set(key: str, default: str) -> bool:
    return str(os.environ.get(key, default)).lower() in ["true", "1"]


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


def get_task_id_service_name(service_name):
    '''Converts the provided service name to a sanitized name as used in task ids.

    For example: /test/integration/foo => test.integration.foo'''
    return service_name.lstrip('/').replace('/', '.')


def get_task_id_prefix(service_name, task_name):
    '''Returns the TaskID prefix to be used for the provided service name and task name.
    The full TaskID would consist of this prefix, plus two underscores and a UUID.

    For example: /test/integration/foo + hello-0-server => test.integration.foo__hello-0-server'''
    return '{}__{}'.format(get_task_id_service_name(service_name), task_name)


def get_deslashed_service_name(service_name):
    # Foldered services have slashes removed: '/test/integration/foo' => 'test__integration__foo'.
    return service_name.lstrip('/').replace('/', '__')


def get_zk_path(service_name):
    return 'dcos-service-{}'.format(get_deslashed_service_name(service_name))


@functools.lru_cache()
def dcos_version():
    return shakedown.dcos_version()


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


def random_string(length=8):
    return ''.join(
        random.choice(
            string.ascii_lowercase +
            string.digits
        ) for _ in range(length)
    )


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
