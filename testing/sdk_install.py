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
import retrying
import traceback

import sdk_cmd
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils

log = logging.getLogger(__name__)

TIMEOUT_SECONDS = 15 * 60

'''List of services which are currently installed via install().
Used by post-test diagnostics to retrieve stuff from currently running services.'''
_installed_service_names = set([])


def get_installed_service_names() -> set:
    '''Returns the a set of service names which had been installed via sdk_install in this session.'''
    return _installed_service_names


@retrying.retry(stop_max_attempt_number=3,
                retry_on_exception=lambda e: isinstance(e, dcos.errors.DCOSException))
def _retried_install_impl(
        package_name,
        service_name,
        expected_running_tasks,
        options={},
        package_version=None,
        timeout_seconds=TIMEOUT_SECONDS):
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
        sdk_tasks.check_running(service_name, expected_running_tasks, timeout_seconds)

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
        package_name, service_name, sdk_utils.pretty_duration(time.time() - start)))

    global _installed_service_names
    _installed_service_names.add(service_name)


def run_janitor(service_name, role, service_account, znode):
    if role is None:
        role = sdk_utils.get_deslashed_service_name(service_name) + '-role'
    if service_account is None:
        service_account = service_name + '-principal'
    if znode is None:
        znode = sdk_utils.get_zk_path(service_name)

    cmd_list = ["docker", "run", "mesosphere/janitor", "/janitor.py",
                "-r", role,
                "-p", service_account,
                "-z", znode,
                "--auth_token={}".format(sdk_utils.dcos_acs_token())]
    cmd = " ".join(cmd_list)

    sdk_cmd.master_ssh(cmd)


@retrying.retry(stop_max_attempt_number=5,
                wait_fixed=5000,
                retry_on_exception=lambda e: isinstance(e, Exception))
def retried_uninstall_package_and_wait(package_name, service_name):
    package_manager = dcos.packagemanager.PackageManager(dcos.cosmos.get_cosmos_url())
    pkg = package_manager.get_package_version(package_name, None)

    log.info('Uninstalling package {} with service name {}'.format(package_name, service_name))
    package_manager.uninstall_app(package_name, all_instances=False, service_name=service_name)

    wait_for_mesos_task_removal(service_name, timeout_sec=600)

    # Uninstall subcommands (if defined)
    if pkg.cli_definition():
        log.info('Uninstalling CLI for package {}'.format(package_name))
        subcommand.uninstall(package_name)


def uninstall(
        package_name,
        service_name,
        role=None,
        service_account=None,
        zk=None):
    start = time.time()

    global _installed_service_names
    try:
        _installed_service_names.remove(service_name)
    except KeyError:
        pass  # allow tests to 'uninstall' up-front

    log.info('Uninstalling {}'.format(service_name))

    try:
        retried_uninstall_package_and_wait(package_name, service_name)
    except Exception:
        log.info('Got exception when uninstalling {}'.format(service_name))
        log.info(traceback.format_exc())
        raise
    finally:
        log.info('Reserved resources post uninstall:')
        sdk_utils.list_reserved_resources()

    cleanup_start = time.time()

    try:
        if sdk_utils.dcos_version_less_than('1.10'):
            @retrying.retry(stop_max_attempt_number=5,
                            wait_fixed=5000,
                            retry_on_exception=lambda e: isinstance(e, Exception))
            def retried_run_janitor(*args, **kwargs):
                run_janitor(*args, **kwargs)

            log.info('Janitoring {}'.format(service_name))
            retried_run_janitor(service_name, role, service_account, zk)
        else:
            log.info('Waiting for Marathon app to be removed {}'.format(service_name))
            sdk_marathon.retried_wait_for_deployment_and_app_removal(
                sdk_marathon.get_app_id(service_name), timeout=TIMEOUT_SECONDS)
    except Exception:
        log.info('Got exception when cleaning up {}'.format(service_name))
        log.info(traceback.format_exc())
        raise
    finally:
        log.info('Reserved resources post cleanup:')
        sdk_utils.list_reserved_resources()

    finish = time.time()

    log.info(
        'Uninstalled {} after pkg({}) + cleanup({}) = total({})'.format(
            service_name,
            sdk_utils.pretty_duration(cleanup_start - start),
            sdk_utils.pretty_duration(finish - cleanup_start),
            sdk_utils.pretty_duration(finish - start)))


def merge_dictionaries(dict1, dict2):
    if (not isinstance(dict2, dict)):
        return dict1
    ret = {}
    for k, v in dict1.items():
        ret[k] = v
    for k, v in dict2.items():
        if (k in dict1 and isinstance(dict1[k], dict) and isinstance(dict2[k], collections.Mapping)):
            ret[k] = merge_dictionaries(dict1[k], dict2[k])
        else:
            ret[k] = dict2[k]
    return ret
