'''Utilities relating to installing services'''

import collections

import dcos.errors
import dcos.marathon
import os
import shakedown
import time
from retrying import retry

import sdk_api
import sdk_plan
import sdk_utils

TIMEOUT_SECONDS = 15 * 60


@retry(stop_max_attempt_number=3, retry_on_exception=lambda e: isinstance(e, dcos.errors.DCOSException))
def retried_shakedown_install(package_name, package_version, service_name,
                              merged_options, timeout_seconds,
                              expected_running_tasks):
    shakedown.install_package(
        package_name,
        package_version=package_version,
        service_name=service_name,
        options_json=merged_options,
        wait_for_completion=True,
        timeout_sec=timeout_seconds,
        expected_running_tasks=expected_running_tasks)


def install(package_name,
            expected_running_tasks,
            service_name=None,
            additional_options={},
            package_version=None,
            timeout_seconds=TIMEOUT_SECONDS,
            wait_scheduler_idle=True):
    if not service_name:
        service_name = package_name
    start = time.time()
    merged_options = get_package_options(additional_options)

    sdk_utils.out('Installing {}/{} with options={} version={}'.format(
        package_name, service_name, merged_options, package_version))

    # 1. Install package, wait for tasks, wait for marathon deployment
    retried_shakedown_install(
        package_name,
        package_version,
        service_name,
        merged_options,
        timeout_seconds,
        expected_running_tasks)

    # 2. Wait for the scheduler to be idle (as implied by deploy plan completion and suppressed bit)
    # This should be skipped ONLY when it's known that the scheduler will be stuck in an incomplete state.
    if wait_scheduler_idle:
        # this can take a while, default is 15 minutes. for example with HDFS, we can hit the expected
        # total task count via FINISHED tasks, without actually completing deployment
        sdk_utils.out("Waiting for {}/{} to finish deployment plan...".format(
            package_name, service_name))
        sdk_plan.wait_for_completed_deployment(service_name, timeout_seconds)

        # given the above wait for plan completion, here we just wait up to 5 minutes
        sdk_utils.out("Waiting for {}/{} to be suppressed...".format(
            package_name, service_name))
        shakedown.wait_for(
            lambda: sdk_api.is_suppressed(service_name),
            noisy=True,
            timeout_seconds=5 * 60)

    sdk_utils.out('Installed {}/{} after {}'.format(package_name, service_name,
                                                    shakedown.pretty_duration(
                                                        time.time() - start)))


def uninstall(service_name,
              package_name=None,
              role=None,
              principal=None,
              zk=None):
    start = time.time()

    if package_name is None:
        package_name = service_name

    if shakedown.dcos_version_less_than("1.10"):
        sdk_utils.out('Uninstalling/janitoring {}'.format(service_name))
        try:
            shakedown.uninstall_package_and_wait(
                package_name, service_name=service_name)
        except (dcos.errors.DCOSException, ValueError) as e:
            sdk_utils.out('Got exception when uninstalling package, ' +
                          'continuing with janitor anyway: {}'.format(e))

        janitor_start = time.time()

        # leading slash removed, other slashes converted to double underscores:
        deslashed_service_name = service_name.lstrip('/').replace('/', '__')
        if role is None:
            role = deslashed_service_name + '-role'
        if principal is None:
            principal = service_name + '-principal'
        if zk is None:
            zk = 'dcos-service-' + deslashed_service_name
        janitor_cmd = ('docker run mesosphere/janitor /janitor.py '
                       '-r {role} -p {principal} -z {zk} --auth_token={auth}')
        shakedown.run_command_on_master(
            janitor_cmd.format(
                role=role,
                principal=principal,
                zk=zk,
                auth=shakedown.run_dcos_command(
                    'config show core.dcos_acs_token')[0].strip()))

        finish = time.time()

        sdk_utils.out(
            'Uninstall done after pkg({}) + janitor({}) = total({})'.format(
                shakedown.pretty_duration(janitor_start - start),
                shakedown.pretty_duration(finish - janitor_start),
                shakedown.pretty_duration(finish - start)))
    else:
        sdk_utils.out('Uninstalling {}'.format(service_name))
        try:
            shakedown.uninstall_package_and_wait(
                package_name, service_name=service_name)
            # service_name may already contain a leading slash:
            marathon_app_id = '/' + service_name.lstrip('/')
            sdk_utils.out('Waiting for no deployments for {}'.format(marathon_app_id))
            shakedown.deployment_wait(TIMEOUT_SECONDS, marathon_app_id)

            # wait for service to be gone according to marathon
            def marathon_dropped_service():
                client = shakedown.marathon.create_client()
                app_list = client.get_apps()
                app_ids = [app['id'] for app in app_list]
                sdk_utils.out('Marathon apps: {}'.format(app_ids))
                matching_app_ids = [
                    app_id for app_id in app_ids if app_id == marathon_app_id
                ]
                if len(matching_app_ids) > 1:
                    sdk_utils.out('Found multiple apps with id {}'.format(
                        marathon_app_id))
                return len(matching_app_ids) == 0
            sdk_utils.out('Waiting for no {} Marathon app'.format(marathon_app_id))
            shakedown.time_wait(marathon_dropped_service, timeout_seconds=TIMEOUT_SECONDS)

        except (dcos.errors.DCOSException, ValueError) as e:
            sdk_utils.out(
                'Got exception when uninstalling package: {}'.format(e))
        finally:
            sdk_utils.list_reserved_resources()


def get_package_options(additional_options={}):
    # expected SECURITY values: 'permissive', 'strict', 'disabled'
    if os.environ.get('SECURITY', '') == 'strict':
        # strict mode requires correct principal and secret to perform install.
        # see also: tools/setup_permissions.sh and tools/create_service_account.sh
        return _merge_dictionary(additional_options, {
            'service': {
                'principal': 'service-acct',
                'secret_name': 'secret',
                'mesos_api_version': 'V0'
            }
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
