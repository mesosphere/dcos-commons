'''Utilities relating to installing services'''

import dcos.errors
import dcos.marathon
import sdk_cmd
import sdk_marathon
import sdk_tasks
import shakedown

import os
import time


def install(
        package_name,
        running_task_count,
        service_name=None,
        additional_options={},
        package_version=None):
    options = get_package_options(additional_options)
    shakedown.install_package_and_wait(
        package_name,
        package_version=package_version,
        options_json=options,
        expected_running_tasks=running_task_count)


def get_package_options(additional_options={}):
    # expected SECURITY values: 'permissive', 'strict', 'disabled'
    if os.environ.get('SECURITY', '') == 'strict':
        # strict mode requires correct principal and secret to perform install.
        # see also: tools/setup_permissions.sh and tools/create_service_account.sh
        return _nested_dict_merge(additional_options, {
            'service': { 'principal': 'service-acct', 'secret_name': 'secret' }
        })
    else:
        return additional_options


def _nested_dict_merge(a, b, path=None):
    '''ripped from http://stackoverflow.com/questions/7204805/dictionaries-of-dictionaries-merge'''
    if path is None:
        path = []
    a = a.copy()
    for key in b:
        if key in a:
            if isinstance(a[key], dict) and isinstance(b[key], dict):
                _nested_dict_merge(a[key], b[key], path + [str(key)])
            elif a[key] == b[key]:
                pass # same leaf value
            else:
                raise Exception('Conflict at %s' % '.'.join(path + [str(key)]))
        else:
            a[key] = b[key]
    return a
