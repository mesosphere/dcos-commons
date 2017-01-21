#!/usr/bin/python

import shakedown

# Utilities relating to service installation

def install(package_name, additional_options={}, package_version=None):
    # expected SECURITY values: 'permissive', 'strict', 'disabled'
    if os.environ.get('SECURITY', '') == 'strict':
        # strict mode requires correct principal and secret to perform install.
        # see also: tools/setup_permissions.sh and tools/create_service_account.sh
        merged_options = _nested_dict_merge(additional_options, {
            'service': { 'principal': 'service-acct', 'secret_name': 'secret' }
        })
    else:
        merged_options = additional_options
    print('Installing {} with options={} version={}'.format(package_name, merged_options, package_version))
    shakedown.install_package_and_wait(
        package_name,
        package_version,
        options_json=merged_options)


def uninstall(service_name, package_name=None):
    if package_name is None:
        package_name = service_name
    print('Uninstalling/janitoring {}'.format(service_name))
    try:
        shakedown.uninstall_package_and_wait(package_name, service_name=service_name)
    except (dcos.errors.DCOSException, ValueError) as e:
        print('Got exception when uninstalling package, continuing with janitor anyway: {}'.format(e))

    janitor_cmd = (
        'docker run mesosphere/janitor /janitor.py '
        '-r {svc}-role -p {svc}-principal -z dcos-service-{svc} --auth_token={auth}')
    shakedown.run_command_on_master(janitor_cmd.format({
        'svc': service_name,
        'auth': shakedown.run_dcos_command('config show core.dcos_acs_token')[0].strip()}))


def _nested_dict_merge(a, b, path=None):
    "ripped from http://stackoverflow.com/questions/7204805/dictionaries-of-dictionaries-merge"
    if path is None: path = []
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
