'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_upgrade IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import json
import logging
import retrying
import shakedown
import tempfile
import traceback

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils

log = logging.getLogger(__name__)

# Installs a universe version of a package, then upgrades it to a test version
#
# (1) Installs Universe version of framework (after uninstalling any test version).
# (2) Upgrades to test version of framework.
def test_upgrade(
        package_name,
        service_name,
        running_task_count,
        additional_options={},
        test_version_additional_options=None,
        timeout_seconds=25*60,
        wait_for_deployment=True):
    # allow providing different options dicts to the universe version vs the test version:
    if test_version_additional_options is None:
        test_version_additional_options = additional_options

    sdk_install.uninstall(package_name, service_name)

    test_version = _get_pkg_version(package_name)
    log.info('Found test version: {}'.format(test_version))

    universe_url = _get_universe_url()

    universe_version = None
    try:
        # Move the Universe repo to the top of the repo list so that we can first install the release version.
        shakedown.remove_package_repo('Universe')
        assert shakedown.add_package_repo('Universe', universe_url, 0)
        log.info('Waiting for Universe release version of {} to appear: version != {}'.format(
            package_name, test_version))
        universe_version = _wait_for_new_package_version(package_name, test_version)

        log.info('Installing Universe version: {}={}'.format(package_name, universe_version))
        sdk_install.install(
            package_name,
            service_name,
            running_task_count,
            additional_options=additional_options,
            timeout_seconds=timeout_seconds,
            wait_for_deployment=wait_for_deployment)
    finally:
        if universe_version:
            # Return the Universe repo back to the bottom of the repo list so that we can upgrade to the build version.
            shakedown.remove_package_repo('Universe')
            assert shakedown.add_package_repo('Universe', universe_url)
            log.info('Waiting for test build version of {} to appear: version != {}'.format(
                package_name, universe_version))
            _wait_for_new_package_version(package_name, universe_version)

    log.info('Upgrading {}: {} => {}'.format(package_name, universe_version, test_version))
    _upgrade_or_downgrade(
        package_name,
        test_version,
        service_name,
        running_task_count,
        test_version_additional_options,
        timeout_seconds,
        wait_for_deployment)


# In the soak cluster, we assume that the Universe version of the framework is already installed.
# Also, we assume that the Universe is the default repo (at --index=0) and the stub repos are already in place,
# so we don't need to add or remove any repos.
#
# (1) Upgrades to test version of framework.
# (2) Downgrades to Universe version.
def soak_upgrade_downgrade(
        package_name,
        service_name,
        running_task_count,
        additional_options={},
        timeout_seconds=25*60,
        wait_for_deployment=True):
    sdk_cmd.run_cli("package install --cli {} --yes".format(package_name))
    version = 'stub-universe'
    log.info('Upgrading to test version: {} {}'.format(package_name, version))
    _upgrade_or_downgrade(
        package_name,
        version,
        service_name,
        running_task_count,
        additional_options,
        timeout_seconds,
        wait_for_deployment)

    # Default Universe is at --index=0
    version = _get_pkg_version(package_name)
    log.info('Downgrading to Universe version: {} {}'.format(package_name, version))
    _upgrade_or_downgrade(
        package_name,
        version,
        service_name,
        running_task_count,
        additional_options,
        timeout_seconds,
        wait_for_deployment)


def _get_universe_url():
    repositories = json.loads(sdk_cmd.run_cli('package repo list --json'))['repositories']
    for repo in repositories:
        if repo['name'] == 'Universe':
            log.info("Found Universe URL: {}".format(repo['uri']))
            return repo['uri']
    assert False, "Unable to find 'Universe' in list of repos: {}".format(repositories)



@retrying.retry(stop_max_attempt_number=15,
                wait_fixed=10000,
                retry_on_result=lambda result: result is None)
def get_config(package_name, service_name):
    """Return the active config for the current service.
    This is retried 15 times, waiting 10s between retries."""

    try:
        # Refrain from dumping the full ServiceSpec to stdout
        target_config = sdk_cmd.svc_cli(
            package_name, service_name, 'debug config target', json=True, print_output=False)
    except Exception as e:
        log.error("Could not determine target config: %s", str(e))
        return None

    return target_config


def _upgrade_or_downgrade(
        package_name,
        to_package_version,
        service_name,
        running_task_count,
        additional_options,
        timeout_seconds,
        wait_for_deployment):

    initial_config = get_config(package_name, service_name)
    task_ids = sdk_tasks.get_task_ids(service_name, '')

    if sdk_utils.dcos_version_less_than("1.10") or shakedown.ee_version() is None:
        log.info('Using marathon upgrade flow to upgrade {} {}'.format(package_name, to_package_version))
        sdk_marathon.destroy_app(service_name)
        sdk_install.install(
            package_name,
            service_name,
            running_task_count,
            additional_options=additional_options,
            package_version=to_package_version,
            timeout_seconds=timeout_seconds,
            wait_for_deployment=wait_for_deployment)
    else:
        log.info('Using CLI upgrade flow to upgrade {} {}'.format(package_name, to_package_version))
        if additional_options:
            with tempfile.NamedTemporaryFile() as opts_f:
                opts_f.write(json.dumps(additional_options).encode('utf-8'))
                opts_f.flush()  # ensure json content is available for the CLI to read below
                sdk_cmd.svc_cli(
                    package_name, service_name,
                    'update start --package-version={} --options={}'.format(to_package_version, opts_f.name))
        else:
            sdk_cmd.svc_cli(
                package_name, service_name,
                'update start --package-version={}'.format(to_package_version))
        # we must manually upgrade the package CLI because it's not done automatically in this flow
        # (and why should it? that'd imply the package CLI replacing itself via a call to the main CLI...)
        sdk_cmd.run_cli(
            'package install --yes --cli --package-version={} {}'.format(to_package_version, package_name))

    if wait_for_deployment:

        updated_config = get_config(package_name, service_name)

        if updated_config == initial_config:
            log.info('No config change detected. Tasks should not be restarted')
            sdk_tasks.check_tasks_not_updated(service_name, '', task_ids)
        else:
            log.info('Checking that all tasks have restarted')
            sdk_tasks.check_tasks_updated(service_name, '', task_ids)

        # this can take a while, default is 15 minutes. for example with HDFS, we can hit the expected
        # total task count via ONCE tasks, without actually completing deployment
        log.info("Waiting for package={} service={} to finish deployment plan...".format(
            package_name, service_name))
        sdk_plan.wait_for_completed_deployment(service_name, timeout_seconds)


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=10*1000,
    retry_on_result=lambda result: result is None)
def _get_pkg_version(package_name):
    cmd = 'package describe {}'.format(package_name)
    # Only log stdout/stderr if there's actually an error.
    rc, stdout, stderr = sdk_cmd.run_raw_cli(cmd, print_output=False)
    if rc != 0:
        log.warning('Failed to run "{}":\nSTDOUT:\n{}\nSTDERR:\n{}'.format(cmd, stdout, stderr))
        return None
    try:
        describe = json.loads(stdout)
        # New location (either 1.10+ or 1.11+):
        version = describe.get('package', {}).get('version', None)
        if version is None:
            # Old location (until 1.9 or until 1.10):
            version = describe['version']
        return version
    except:
        log.warning('Failed to extract package version from "{}":\nSTDOUT:\n{}\nSTDERR:\n{}'.format(cmd, stdout, stderr))
        log.warning(traceback.format_exc())
        return None


@retrying.retry(
    wait_fixed=1000,
    stop_max_delay=60*1000,
    retry_on_result=lambda result: result is None)
def _wait_for_new_package_version(package_name, prev_version):
    cur_version = _get_pkg_version(package_name)
    log.info('Current version of {} is: {}'.format(package_name, cur_version))
    return cur_version if cur_version != prev_version else None
