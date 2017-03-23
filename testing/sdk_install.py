'''Utilities relating to installing services'''

import collections
import dcos.errors
import dcos.marathon
import shakedown

import os
import time


def install(
        package_name,
        running_task_count,
        service_name=None,
        additional_options={},
        package_version=None):
    # small addition over shakedown: automatically include secure mode config when needed
    shakedown.install_package_and_wait(
        package_name,
        package_version=package_version,
        service_name=service_name,
        options_json=get_package_options(additional_options),
        expected_running_tasks=running_task_count)


def uninstall(package_name, service_name=None, wipe_agents=True):
    if not service_name:
        service_name = package_name
    if wipe_agents:
        framework_id = shakedown.get_service_framework_id(service_name)
    shakedown.uninstall_package_and_data(package_name, service_name)
    if wipe_agents and framework_id is not None:
        gc_frameworks(framework_id)


def gc_frameworks(framework_id):
    '''Reclaims private agent disk space consumed by Mesos but not yet garbage collected'''
    for host in shakedown.get_private_agents():
        shakedown.run_command(
            host,
            "sudo rm -rf /var/lib/mesos/slave/slaves/*/frameworks/{}/".format(framework_id))


def get_package_options(additional_options={}):
    # expected SECURITY values: 'permissive', 'strict', 'disabled'
    if os.environ.get('SECURITY', '') == 'strict':
        # strict mode requires correct principal and secret to perform install.
        # see also: tools/setup_permissions.sh and tools/create_service_account.sh
        return _merge_dictionary(additional_options, {
            'service': { 'principal': 'service-acct', 'secret_name': 'secret' }
        })
    else:
        return additional_options


def _merge_dictionary(dict1, dict2):
    if (not isinstance(dict2, dict)):
        return dict1
    ret = {}
    for k, v in dict1.items():
        ret[k] = v
    for k, v in dict2.items():
        if (k in dict1 and isinstance(dict1[k], dict)
            and isinstance(dict2[k], collections.Mapping)):
            ret[k] = _merge_dictionary(dict1[k], dict2[k])
        else:
            ret[k] = dict2[k]
    return ret
