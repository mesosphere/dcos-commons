'''Utilities relating to installing services'''

import collections
import dcos.errors
import dcos.marathon
import sdk_api
import sdk_plan
import sdk_spin
import sdk_tasks
import sdk_utils
import shakedown

import os
import time


def install(
        package_name,
        running_task_count,
        service_name=None,
        additional_options={},
        package_version=None,
        check_suppression=True):
    if not service_name:
        service_name = package_name
    start = time.time()
    merged_options = get_package_options(additional_options)

    sdk_utils.out('Installing {} with options={} version={}'.format(
        package_name, merged_options, package_version))

    # install_package_and_wait silently waits for all marathon deployments to clear.
    # to give some visibility, install in the following order:
    # 1. install package
    shakedown.install_package(
        package_name,
        package_version=package_version,
        options_json=merged_options)

    # 2. wait for expected tasks to come up
    sdk_utils.out("Waiting for expected tasks to come up...")
    sdk_tasks.check_running(service_name, running_task_count)
    sdk_plan.wait_for_completed_deployment(service_name)

    # 3. check service health
    marathon_client = dcos.marathon.create_client()
    def is_deployment_finished():
        # TODO(nickbp): upstream fix to shakedown, which currently checks for ANY deployments rather
        #               than the one we care about
        deploying_apps = set([])
        sdk_utils.out("Getting deployments")
        deployments = marathon_client.get_deployments()
        sdk_utils.out("Found {} deployments".format(len(deployments)))
        for deployment in deployments:
            sdk_utils.out("Deployment: {}".format(deployment))
            for app in deployment.get('affectedApps', []):
                sdk_utils.out("Adding {}".format(app))
                deploying_apps.add(app)
        sdk_utils.out('Checking that deployment of {} has ended:\n- Deploying apps: {}'.format(service_name, deploying_apps))
        return not '/{}'.format(service_name) in deploying_apps
    sdk_utils.out("Waiting for marathon deployment to finish...")
    sdk_spin.time_wait_noisy(is_deployment_finished)

    # 4. Ensure the framework is suppressed.
    #
    # This is only configurable in order to support installs from
    # Universe during the upgrade_downgrade tests, because currently
    # the suppression endpoint isn't supported by all frameworks in
    # Universe.  It can be removed once all frameworks rely on
    # dcos-commons >= 0.13.
    if check_suppression:
        sdk_utils.out("Waiting for framework to be suppressed...")
        sdk_spin.time_wait_noisy(
            lambda: sdk_api.is_suppressed(service_name))

    sdk_utils.out('Install done after {}'.format(sdk_spin.pretty_time(time.time() - start)))


def uninstall(service_name, package_name=None):
    start = time.time()

    if package_name is None:
        package_name = service_name
    sdk_utils.out('Uninstalling/janitoring {}'.format(service_name))
    try:
        shakedown.uninstall_package_and_wait(package_name, service_name=service_name)
    except (dcos.errors.DCOSException, ValueError) as e:
        sdk_utils.out('Got exception when uninstalling package, ' +
              'continuing with janitor anyway: {}'.format(e))

    janitor_start = time.time()

    janitor_cmd = (
        'docker run mesosphere/janitor /janitor.py '
        '-r {svc}-role -p {svc}-principal -z dcos-service-{svc} --auth_token={auth}')
    shakedown.run_command_on_master(janitor_cmd.format(
        svc=service_name,
        auth=shakedown.run_dcos_command('config show core.dcos_acs_token')[0].strip()))

    finish = time.time()

    sdk_utils.out('Uninstall done after pkg({}) + janitor({}) = total({})'.format(
        sdk_spin.pretty_time(janitor_start - start),
        sdk_spin.pretty_time(finish - janitor_start),
        sdk_spin.pretty_time(finish - start)))


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
