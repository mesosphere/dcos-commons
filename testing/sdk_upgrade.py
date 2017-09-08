'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_upgrade IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import json
import logging
import re
import shakedown
import tempfile

import sdk_api
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks

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

    universe_version = ""
    try:
        # Move the Universe repo to the top of the repo list
        shakedown.remove_package_repo('Universe')
        _add_repo('Universe', universe_url, test_version, 0, package_name)

        universe_version = _get_pkg_version(package_name)

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
            # Return the Universe repo back to the bottom of the repo list
            shakedown.remove_package_repo('Universe')
            _add_last_repo('Universe', universe_url, universe_version, package_name)

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
    print('Upgrading to test version: {} {}'.format(package_name, version))
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
    print('Downgrading to Universe version: {} {}'.format(package_name, version))
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


def _upgrade_or_downgrade(
        package_name,
        to_package_version,
        service_name,
        running_task_count,
        additional_options,
        timeout_seconds,
        wait_for_deployment):
    task_ids = sdk_tasks.get_task_ids(service_name, '')
    if shakedown.dcos_version_less_than("1.10") or shakedown.ee_version() is None:
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
                opts_f.flush() # ensure json content is available for the CLI to read below
                sdk_cmd.svc_cli(
                    package_name, service_name,
                    'update start --package-version={} --options={}'.format(to_package_version, opts_f.name))
        else:
            sdk_cmd.svc_cli(
                package_name, service_name,
                'update start --package-version={}'.format(to_package_version))

    if wait_for_deployment:
        log.info('Checking that all tasks have restarted')
        sdk_tasks.check_tasks_updated(service_name, '', task_ids)

        # this can take a while, default is 15 minutes. for example with HDFS, we can hit the expected
        # total task count via FINISHED tasks, without actually completing deployment
        log.info("Waiting for {}/{} to finish deployment plan...".format(
            package_name, service_name))
        sdk_plan.wait_for_completed_deployment(service_name, timeout_seconds)

        # given the above wait for plan completion, here we just wait up to 5 minutes
        if shakedown.dcos_version_less_than("1.9"):
            log.info("Skipping `is_suppressed` check for %s/%s as this is only suppored starting in version 1.9",
                     package_name, service_name)
        else:
            log.info("Waiting for %s/%s to be suppressed...", package_name, service_name)
            shakedown.wait_for(
                lambda: sdk_api.is_suppressed(service_name),
                noisy=True,
                timeout_seconds=5 * 60)


def _get_pkg_version(package_name):
    return re.search(
        r'"version": "(\S+)"',
        sdk_cmd.run_cli('package describe {}'.format(package_name), print_output=False)).group(1)


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
