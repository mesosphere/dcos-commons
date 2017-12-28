'''Utilities relating to installing services

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_install IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import collections
import logging
import time

import dcos.cosmos
import dcos.errors
import dcos.marathon
import dcos.packagemanager
import dcos.subcommand
from retrying import retry
import shakedown

import sdk_cmd
import sdk_marathon
import sdk_plan
import sdk_utils

log = logging.getLogger(__name__)

TIMEOUT_SECONDS = 15 * 60


@retry(stop_max_attempt_number=3, retry_on_exception=lambda e: isinstance(e, dcos.errors.DCOSException))
def _retried_install_impl(
        package_name,
        service_name,
        expected_running_tasks,
        options={},
        package_version=None,
        timeout_seconds=TIMEOUT_SECONDS):
    '''Cleaned up version of shakedown's package_install().'''
    package_manager = dcos.packagemanager.PackageManager(dcos.cosmos.get_cosmos_url())
    pkg = package_manager.get_package_version(package_name, package_version)

    if package_version is None:
        # Get the resolved version for logging below
        package_version = 'auto:{}'.format(pkg.version())

    log.info('Installing package={} service={} with options={} version={}'.format(
        package_name, service_name, options, package_version))

    # Trigger package install, but only if it's not already installed.
    # We expect upstream to have confirmed that it wasn't already installed beforehand.
    if sdk_marathon.app_exists(service_name):
        log.info('Marathon app={} exists, skipping package install call'.format(service_name))
    else:
        package_manager.install_app(pkg, options)

    # Install CLI while package starts to install
    if pkg.cli_definition():
        log.info('Installing CLI for package={}'.format(package_name))
        dcos.subcommand.install(pkg)

    # Wait for expected tasks to come up
    if expected_running_tasks > 0:
        shakedown.wait_for_service_tasks_running(
            service_name, expected_running_tasks, timeout_seconds)

    # Wait for completed marathon deployment
    app_id = pkg.marathon_json(options).get('id')
    shakedown.deployment_wait(timeout_seconds, app_id)


def install(
        package_name,
        service_name,
        expected_running_tasks,
        additional_options={},
        package_version=None,
        timeout_seconds=TIMEOUT_SECONDS,
        wait_for_deployment=True,
        insert_strict_options=True):
    start = time.time()

    # If the package is already installed at this point, fail immediately.
    if sdk_marathon.app_exists(service_name):
        raise dcos.errors.DCOSException('Service is already installed: {}'.format(service_name))

    if insert_strict_options and sdk_utils.is_strict_mode():
        # strict mode requires correct principal and secret to perform install.
        # see also: sdk_security.py
        options = merge_dictionaries({
            'service': {
                'service_account': 'service-acct',
                'principal': 'service-acct',
                'service_account_secret': 'secret',
                'secret_name': 'secret'
            }
        }, additional_options)
    else:
        options = additional_options

    # 1. Install package, wait for tasks, wait for marathon deployment
    _retried_install_impl(
        package_name,
        service_name,
        expected_running_tasks,
        options,
        package_version,
        timeout_seconds)

    # 2. Wait for the scheduler to be idle (as implied by deploy plan completion and suppressed bit)
    # This should be skipped ONLY when it's known that the scheduler will be stuck in an incomplete
    # state, or if the thing being installed doesn't have a deployment plan (e.g. standalone app)
    if wait_for_deployment:
        # this can take a while, default is 15 minutes. for example with HDFS, we can hit the expected
        # total task count via FINISHED tasks, without actually completing deployment
        log.info('Waiting for package={} service={} to finish deployment plan...'.format(
            package_name, service_name))
        sdk_plan.wait_for_completed_deployment(service_name, timeout_seconds)

    log.info('Installed package={} service={} after {}'.format(
        package_name, service_name, shakedown.pretty_duration(time.time() - start)))


@retry(stop_max_attempt_number=5, wait_fixed=5000, retry_on_exception=lambda e: isinstance(e, dcos.errors.DCOSException))
def uninstall(
        package_name,
        service_name,
        role=None,
        service_account=None,
        zk=None):
    _uninstall(
        package_name,
        service_name,
        role,
        service_account,
        zk)


def _uninstall(
        package_name,
        service_name,
        role=None,
        service_account=None,
        zk=None):
    start = time.time()

    if sdk_utils.dcos_version_less_than('1.10'):
        log.info('Uninstalling/janitoring {}'.format(service_name))
        try:
            shakedown.uninstall_package_and_wait(
                package_name, service_name=service_name)
        except (dcos.errors.DCOSException, ValueError) as e:
            log.info('Got exception when uninstalling package, ' +
                          'continuing with janitor anyway: {}'.format(e))
            if 'marathon' in str(e):
                log.info('Detected a probable marathon flake. Raising so retry will trigger.')
                raise

        janitor_start = time.time()

        # leading slash removed, other slashes converted to double underscores:
        deslashed_service_name = service_name.lstrip('/').replace('/', '__')
        if role is None:
            role = deslashed_service_name + '-role'
        if service_account is None:
            service_account = service_name + '-principal'
        if zk is None:
            zk = 'dcos-service-' + deslashed_service_name
        janitor_cmd = ('docker run mesosphere/janitor /janitor.py '
                       '-r {role} -p {service_account} -z {zk} --auth_token={auth}')
        shakedown.run_command_on_master(
            janitor_cmd.format(
                role=role,
                service_account=service_account,
                zk=zk,
                auth=sdk_cmd.run_cli('config show core.dcos_acs_token', print_output=False).strip()))

        finish = time.time()

        log.info(
            'Uninstall done after pkg({}) + janitor({}) = total({})'.format(
                shakedown.pretty_duration(janitor_start - start),
                shakedown.pretty_duration(finish - janitor_start),
                shakedown.pretty_duration(finish - start)))
    else:
        log.info('Uninstalling {}'.format(service_name))
        try:
            shakedown.uninstall_package_and_wait(
                package_name, service_name=service_name)
            # service_name may already contain a leading slash:
            marathon_app_id = '/' + service_name.lstrip('/')
            log.info('Waiting for no deployments for {}'.format(marathon_app_id))
            shakedown.deployment_wait(TIMEOUT_SECONDS, marathon_app_id)

            # wait for service to be gone according to marathon
            client = shakedown.marathon.create_client()
            def marathon_dropped_service():
                app_ids = [app['id'] for app in client.get_apps()]
                log.info('Marathon apps: {}'.format(app_ids))
                matching_app_ids = [
                    app_id for app_id in app_ids if app_id == marathon_app_id
                ]
                if len(matching_app_ids) > 1:
                    log.warning('Found multiple apps with id {}'.format(
                        marathon_app_id))
                return len(matching_app_ids) == 0
            log.info('Waiting for no {} Marathon app'.format(marathon_app_id))
            shakedown.time_wait(marathon_dropped_service, timeout_seconds=TIMEOUT_SECONDS)

        except (dcos.errors.DCOSException, ValueError) as e:
            log.info(
                'Got exception when uninstalling package: {}'.format(e))
            if 'marathon' in str(e):
                log.info('Detected a probable marathon flake. Raising so retry will trigger.')
                raise
        finally:
            sdk_utils.list_reserved_resources()


def merge_dictionaries(dict1, dict2):
    if (not isinstance(dict2, dict)):
        return dict1
    ret = {}
    for k, v in dict1.items():
        ret[k] = v
    for k, v in dict2.items():
        if (k in dict1 and isinstance(dict1[k], dict)
                and isinstance(dict2[k], collections.Mapping)):
            ret[k] = merge_dictionaries(dict1[k], dict2[k])
        else:
            ret[k] = dict2[k]
    return ret
