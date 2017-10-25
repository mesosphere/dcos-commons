import json
import logging
import re
import retrying
import shakedown
import tempfile

import sdk_cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_tasks
import sdk_utils

log = logging.getLogger(__name__)

# Installs a universe version, then upgrades it to a test version
#
# (1) Installs Universe version of framework (after uninstalling any test version).
# (2) Upgrades to test version of framework.
#
# With beta packages, the Universe package name is different from the test package name.
# We install both with the same service name=test_package_name.


def test_upgrade(
        universe_package_name,
        test_package_name,
        running_task_count,
        service_name=None,
        additional_options={},
        test_version_options=None,
        timeout_seconds=25*60,
        wait_for_deployment=True):
    # allow a service name which is different from the package name (common with e.g. folders):
    if service_name is None:
        service_name = test_package_name
    # allow providing different options dicts to the universe version vs the test version:
    if test_version_options is None:
        test_version_options = additional_options

    # make sure BOTH are uninstalled...
    install.uninstall(service_name, package_name=universe_package_name)
    if universe_package_name is not test_package_name:
        install.uninstall(service_name, package_name=test_package_name)

    test_version = _get_pkg_version(test_package_name)
    log.info('Found test version: {}'.format(test_version))

    universe_url = _get_universe_url()

    universe_version = ""
    try:
        # Move the Universe repo to the top of the repo list
        shakedown.remove_package_repo('Universe')
        _add_repo('Universe', universe_url, test_version, 0, universe_package_name)

        universe_version = _get_pkg_version(universe_package_name)

        log.info('Installing Universe version: {}={}'.format(universe_package_name, universe_version))
        # Keep the service name the same throughout the test
        install.install(
            universe_package_name,
            running_task_count,
            service_name=service_name,
            additional_options=additional_options,
            timeout_seconds=timeout_seconds,
            wait_for_deployment=wait_for_deployment)
    finally:
        if universe_version:
            # Return the Universe repo back to the bottom of the repo list
            shakedown.remove_package_repo('Universe')
            _add_last_repo('Universe', universe_url, universe_version, test_package_name)

    log.info('Upgrading {} to {}={}'.format(universe_package_name, test_package_name, test_version))
    _upgrade_or_downgrade(
        universe_package_name,
        test_package_name,
        test_version,
        service_name,
        running_task_count,
        test_version_options,
        timeout_seconds)


# Downgrades an installed test version back to a universe version
#
# (3) Downgrades to Universe version.
# (4) Upgrades back to test version, as clean up (if reinstall_test_version == True).
def test_downgrade(
        universe_package_name,
        test_package_name,
        running_task_count,
        service_name=None,
        additional_options={},
        test_version_options=None,
        reinstall_test_version=True,
        timeout_seconds=25*60):
    # allow a service name which is different from the package name (common with e.g. folders):
    if service_name is None:
        service_name = test_package_name
    # allow providing different options dicts to the universe version vs the test version:
    if test_version_options is None:
        test_version_options = additional_options

    test_version = _get_pkg_version(test_package_name)
    log.info('Found test version: {}'.format(test_version))

    universe_url = _get_universe_url()

    universe_version = ""
    try:
        # Move the Universe repo to the top of the repo list
        shakedown.remove_package_repo('Universe')
        _add_repo('Universe', universe_url, test_version, 0, universe_package_name)

        universe_version = _get_pkg_version(universe_package_name)

        log.info('Downgrading to Universe version: {}={}'.format(universe_package_name, universe_version))
        _upgrade_or_downgrade(
            test_package_name,
            universe_package_name,
            universe_version,
            service_name,
            running_task_count,
            additional_options,
            timeout_seconds)

    finally:
        if universe_version:
            # Return the Universe repo back to the bottom of the repo list
            shakedown.remove_package_repo('Universe')
            _add_last_repo('Universe', universe_url, universe_version, test_package_name)

    if reinstall_test_version:
        log.info('Re-upgrading to test version before exiting: {}={}'.format(test_package_name, test_version))
        _upgrade_or_downgrade(
            universe_package_name,
            test_package_name,
            test_version,
            service_name,
            running_task_count,
            test_version_options,
            timeout_seconds)
    else:
        log.info('Skipping reinstall of test version {}={}, uninstalling universe version {}={}'.format(
            test_package_name, test_version, universe_package_name, universe_version))
        install.uninstall(service_name, package_name=universe_package_name)


# (1) Installs Universe version of framework (after uninstalling any test version).
# (2) Upgrades to test version of framework.
# (3) Downgrades to Universe version.
# (4) Upgrades back to test version, as clean up (if reinstall_test_version == True).
#
# With beta packages, the Universe package name is different from the test package name.
# We install both with the same service name=test_package_name.
def test_upgrade_downgrade(
        universe_package_name,
        test_package_name,
        running_task_count,
        service_name=None,
        additional_options={},
        test_version_options=None,
        reinstall_test_version=True):
    test_upgrade(
        universe_package_name,
        test_package_name,
        running_task_count,
        service_name,
        additional_options,
        test_version_options)
    test_downgrade(
        universe_package_name,
        test_package_name,
        running_task_count,
        service_name,
        additional_options,
        test_version_options,
        reinstall_test_version)


# In the soak cluster, we assume that the Universe version of the framework is already installed.
# Also, we assume that the Universe is the default repo (at --index=0) and the stub repos are already in place,
# so we don't need to add or remove any repos.
#
# (1) Upgrades to test version of framework.
# (2) Downgrades to Universe version.
def soak_upgrade_downgrade(
        universe_package_name,
        test_package_name,
        service_name,
        running_task_count,
        install_options={},
        timeout_seconds=25*60):
    sdk_cmd.run_cli("package install --cli {} --yes".format(universe_package_name))
    version = 'stub-universe'
    print('Upgrading to test version: {} => {} {}'.format(universe_package_name, test_package_name, version))
    _upgrade_or_downgrade(
        universe_package_name,
        test_package_name,
        version,
        service_name,
        running_task_count,
        install_options,
        timeout_seconds)

    # Default Universe is at --index=0
    version = _get_pkg_version(universe_package_name)
    print('Downgrading to Universe version: {} => {} {}'.format(test_package_name, universe_package_name, version))
    _upgrade_or_downgrade(
        test_package_name,
        universe_package_name,
        version,
        service_name,
        running_task_count,
        install_options,
        timeout_seconds)


def _get_universe_url():
    repositories = json.loads(sdk_cmd.run_cli('package repo list --json'))['repositories']
    for repo in repositories:
        if repo['name'] == 'Universe':
            log.info("Found Universe URL: {}".format(repo['uri']))
            return repo['uri']
    assert False, "Unable to find 'Universe' in list of repos: {}".format(repositories)


@retrying.retry(stop_max_attempt_number=5,
                wait_fixed=30000,
                retry_on_result=lambda result: result is None)
def get_config(package_name, service_name):
    """Return the active config for the current service.
    This is retried 5 times, waiting 30s between retries."""

    try:
        target_config = sdk_cmd.svc_cli(package_name, service_name,
                                        'config target', json=True)
    except Exception as e:
        log.error("Could not determine target config: %s", str(e))
        return None

    return target_config


def _upgrade_or_downgrade(
        from_package_name,
        to_package_name,
        to_package_version,
        service_name,
        running_task_count,
        additional_options,
        timeout_seconds):
    initial_config = get_config(from_package_name, service_name)
    task_ids = sdk_tasks.get_task_ids(service_name, '')

    if sdk_utils.dcos_version_less_than("1.10") or shakedown.ee_version() is None or from_package_name != to_package_name:
        log.info('Using marathon upgrade flow to upgrade {} => {} {}'.format(from_package_name, to_package_name, to_package_version))
        marathon.destroy_app(service_name)
        install.install(
            to_package_name,
            running_task_count,
            service_name=service_name,
            additional_options=additional_options,
            timeout_seconds=timeout_seconds,
            package_version=to_package_version)
    else:
        log.info('Using CLI upgrade flow to upgrade {} => {} {}'.format(from_package_name, to_package_name, to_package_version))
        if additional_options:
            with tempfile.NamedTemporaryFile() as opts_f:
                opts_f.write(json.dumps(additional_options).encode('utf-8'))
                opts_f.flush()  # ensure json content is available for the CLI
                sdk_cmd.run_cli(
                    '{} --name={} update start --package-version={} --options={}'.format(to_package_name, service_name, to_package_version, opts_f.name))
        else:
            sdk_cmd.run_cli(
                '{} --name={} update start --package-version={}'.format(to_package_name, service_name, to_package_version))

    updated_config = get_config(to_package_name, service_name)

    if updated_config == initial_config:
        log.info('No config change detected. Tasks should not be restarted')
        sdk_tasks.check_tasks_not_updated(service_name, '', task_ids)
    else:
        log.info('Checking that all tasks have restarted')
        sdk_tasks.check_tasks_updated(service_name, '', task_ids)


def _get_pkg_version(package_name):
    return re.search(
        r'"version": "(\S+)"',
        sdk_cmd.run_cli('package describe {}'.format(package_name))).group(1)


# Default repo is the one at index=0.
def _add_repo(repo_name, repo_url, prev_version, index, default_repo_package_name):
    assert shakedown.add_package_repo(
        repo_name,
        repo_url,
        index)
    # Make sure the new default repo packages are available
    _wait_for_new_default_version(prev_version, default_repo_package_name)


def _add_last_repo(repo_name, repo_url, prev_version, default_repo_package_name):
    assert shakedown.add_package_repo(
        repo_name,
        repo_url)
    # Make sure the new default repo packages are available
    _wait_for_new_default_version(prev_version, default_repo_package_name)


def _wait_for_new_default_version(prev_version, default_repo_package_name):
    shakedown.wait_for(lambda: _get_pkg_version(default_repo_package_name) != prev_version, noisy=True)
