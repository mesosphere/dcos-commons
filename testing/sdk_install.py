'''Utilities relating to installing services

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_install IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging
import time

import dcos.cosmos
import dcos.errors
import dcos.marathon
import dcos.packagemanager
import dcos.subcommand
import retrying
import shakedown

import sdk_cmd
import sdk_marathon
import sdk_plan
import sdk_utils

log = logging.getLogger(__name__)

TIMEOUT_SECONDS = 15 * 60

'''List of services which are currently installed via install().
Used by post-test diagnostics to retrieve stuff from currently running services.'''
_installed_service_names = set([])

'''List of dead agents which should be ignored when checking for orphaned resources.
Used by uninstall when validating that an uninstall completed successfully.'''
_dead_agent_hosts = set([])


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
        raise Exception('Service is already installed: {}'.format(service_name))

    if insert_strict_options and sdk_utils.is_strict_mode():
        # strict mode requires correct principal and secret to perform install.
        # see also: sdk_security.py
        options = sdk_utils.merge_dictionaries({
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

    global _installed_service_names
    _installed_service_names.add(service_name)


@retrying.retry(stop_max_attempt_number=5,
                wait_fixed=5000,
                retry_on_exception=lambda e: isinstance(e, Exception))
def _retried_run_janitor(service_name):
    auth_token = sdk_cmd.run_cli('config show core.dcos_acs_token', print_output=False).strip()

    cmd_list = ["docker", "run", "mesosphere/janitor", "/janitor.py",
                "-r", sdk_utils.get_role(service_name),
                "-p", service_name + '-principal',
                "-z", sdk_utils.get_zk_path(service_name),
                "--auth_token={}".format(auth_token)]

    sdk_cmd.master_ssh(" ".join(cmd_list))


@retrying.retry(stop_max_attempt_number=5,
                wait_fixed=5000,
                retry_on_exception=lambda e: isinstance(e, Exception))
def _retried_uninstall_package_and_wait(*args, **kwargs):
    shakedown.uninstall_package_and_wait(*args, **kwargs)


def _verify_completed_uninstall(service_name):
    state_summary = sdk_cmd.cluster_request('GET', '/mesos/state-summary').json()

    # There should be no orphaned resources in the state summary (DCOS-30314)
    orphaned_resources = 0
    ignored_orphaned_resources = 0
    service_role = sdk_utils.get_role(service_name)
    for agent in state_summary['slaves']:
        # resources should be grouped by role. check for any resources in our expected role:
        matching_reserved_resources = agent['reserved_resources'].get(service_role)
        if matching_reserved_resources:
            if agent['hostname'] in _dead_agent_hosts:
                # The test told us ahead of time to expect orphaned resources on this host.
                log.info('Ignoring orphaned resources on agent {}/{}: {}'.format(
                    agent['id'], agent['hostname'], matching_reserved_resources))
                ignored_orphaned_resources += len(matching_reserved_resources)
            else:
                log.error('Orphaned resources on agent {}/{}: {}'.format(
                    agent['id'], agent['hostname'], matching_reserved_resources))
                orphaned_resources += len(matching_reserved_resources)
    if orphaned_resources:
        log.error('{} orphaned resources (plus {} ignored) after uninstall of {}'.format(
            orphaned_resources, ignored_orphaned_resources, service_name))
        log.error(state_summary)
        raise Exception('Found {} orphaned resources (plus {} ignored) after uninstall of {}'.format(
            orphaned_resources, ignored_orphaned_resources, service_name))
    elif ignored_orphaned_resources:
        log.info('Ignoring {} orphaned resources after uninstall of {}'.format(
            ignored_orphaned_resources, service_name))
        log.info(state_summary)
    else:
        log.info('No orphaned resources for role {} were found'.format(service_role))

    # There should be no framework entry for this service in the state summary (DCOS-29474)
    orphaned_frameworks = [fwk for fwk in state_summary['frameworks'] if fwk['name'] == service_name]
    if orphaned_frameworks:
        log.error('{} orphaned frameworks named {} after uninstall of {}: {}'.format(
            len(orphaned_frameworks), service_name, service_name, orphaned_frameworks))
        log.error(state_summary)
        raise Exception('Found {} orphaned frameworks named {} after uninstall of {}: {}'.format(
            len(orphaned_frameworks), service_name, service_name, orphaned_frameworks))
    log.info('No orphaned frameworks for service {} were found'.format(service_name))


def ignore_dead_agent(agent_host):
    '''Marks the specified agent as destroyed. When uninstall() is next called, any orphaned
    resources against this agent will be logged but will not result in a thrown exception.
    '''
    _dead_agent_hosts.add(agent_host)
    log.info('Added {} to expected dead agents for resource validation purposes: {}'.format(
        agent_host, _dead_agent_hosts))


def uninstall(package_name, service_name):
    '''Uninstalls the specified service from the cluster, and verifies that its resources and
    framework were correctly cleaned up after the uninstall has completed. Any agents which are
    expected to have orphaned resources (e.g. due to being shut down) should be passed to
    ignore_dead_agent() before triggering the uninstall.
    '''
    start = time.time()

    log.info('Uninstalling {}'.format(service_name))

    try:
        _retried_uninstall_package_and_wait(package_name, service_name=service_name)
    except Exception:
        log.exception('Got exception when uninstalling {}'.format(service_name))
        raise

    cleanup_start = time.time()

    try:
        if sdk_utils.dcos_version_less_than('1.10'):
            # 1.9 and earlier: Run janitor to unreserve resources
            log.info('Janitoring {}'.format(service_name))
            _retried_run_janitor(service_name)
        else:
            # 1.10 and later: Wait for uninstall scheduler to finish and be removed by Cosmos
            log.info('Waiting for Marathon app to be removed {}'.format(service_name))
            sdk_marathon.retried_wait_for_deployment_and_app_removal(
                sdk_marathon.get_app_id(service_name), timeout=TIMEOUT_SECONDS)
    except Exception:
        log.exception('Got exception when cleaning up {}'.format(service_name))
        raise

    finish = time.time()

    log.info(
        'Uninstalled {} after pkg({}) + cleanup({}) = total({})'.format(
            service_name,
            shakedown.pretty_duration(cleanup_start - start),
            shakedown.pretty_duration(finish - cleanup_start),
            shakedown.pretty_duration(finish - start)))

    # Sanity check: Verify that all resources and the framework have been successfully cleaned up,
    # and throw an exception if anything is left over (uninstall bug?)
    _verify_completed_uninstall(service_name)

    # Finally, remove the service from the installed list (used by sdk_diag)
    global _installed_service_names
    try:
        _installed_service_names.remove(service_name)
    except KeyError:
        pass  # Expected when tests preemptively uninstall at start of test
