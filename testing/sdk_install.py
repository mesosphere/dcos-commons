'''Utilities relating to installing services'''

import dcos.errors
import dcos.marathon
import sdk_cmd
import sdk_marathon
import sdk_spin
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
    if not service_name:
        service_name = package_name
    start = time.time()
    merged_options = get_package_options(additional_options)
    print('Installing {} with options={} version={}'.format(
        package_name, merged_options, package_version))
    # install_package_and_wait silently waits for all marathon deployments to clear.
    # to give some visibility, install in the following order:
    # 1. install package
    shakedown.install_package(
        package_name,
        package_version=package_version,
        options_json=merged_options)
    # 2. wait for expected tasks to come up
    sdk_tasks.check_running(service_name, running_task_count)
    # 3. check service health
    app_id = sdk_marathon.get_config(service_name)['id']
    marathon_client = dcos.marathon.create_client()
    def fn():
        # TODO(nickbp): upstream fix to shakedown, which currently checks for ANY deployments rather
        #               than the one we care about
        deploying_apps = set([])
        for d in marathon_client.get_deployments():
            for a in d.get('affectedApps', []):
                deploying_apps.add(a)
        print('Checking deployment of {} has ended:\n- Deploying apps: {}'.format(
            service_name, deploying_apps))
        return not '/{}'.format(service_name) in deploying_apps
    sdk_spin.time_wait_noisy(lambda: fn())
    print('Install done after {}'.format(sdk_spin.pretty_time(time.time() - start)))


def uninstall(service_name, package_name=None):
    start = time.time()

    if package_name is None:
        package_name = service_name
    print('Uninstalling/janitoring {}'.format(service_name))
    try:
        shakedown.uninstall_package_and_wait(package_name, service_name=service_name)
    except (dcos.errors.DCOSException, ValueError) as e:
        print('Got exception when uninstalling package, ' +
              'continuing with janitor anyway: {}'.format(e))

    janitor_start = time.time()

    janitor_cmd = (
        'docker run mesosphere/janitor /janitor.py '
        '-r {svc}-role -p {svc}-principal -z dcos-service-{svc} --auth_token={auth}')
    shakedown.run_command_on_master(janitor_cmd.format(
        svc=service_name,
        auth=shakedown.run_dcos_command('config show core.dcos_acs_token')[0].strip()))

    finish = time.time()

    print('Uninstall done after pkg({}) + janitor({}) = total({})'.format(
        sdk_spin.pretty_time(janitor_start - start),
        sdk_spin.pretty_time(finish - janitor_start),
        sdk_spin.pretty_time(finish - start)))


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
    "ripped from http://stackoverflow.com/questions/7204805/dictionaries-of-dictionaries-merge"
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
