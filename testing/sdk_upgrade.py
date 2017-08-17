import base64
import gzip
import json
import logging
import re
import shakedown
import tempfile

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_plan as plan
import sdk_tasks as tasks

log = logging.getLogger(__name__)

PACKAGE_NAME_LABEL = 'DCOS_PACKAGE_NAME'
PACKAGE_METADATA_LABEL = 'DCOS_PACKAGE_METADATA'
PACKAGE_DEF_LABEL = 'DCOS_PACKAGE_DEFINITION'

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

    log.info('Upgrading to test version: {}={}'.format(test_package_name, test_version))
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
    version = 'stub-universe'
    print('Upgrading to test version: {} => {} {}'.format(universe_package_name, test_package_name, version))
    _upgrade_or_downgrade(
        universe_package_name,
        test_package_name,
        version,
        service_name,
        running_task_count,
        install_options,
        timeout_seconds,
        package_version)

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
    repositories = json.loads(cmd.run_cli('package repo list --json'))['repositories']
    for repo in repositories:
        if repo['name'] == 'Universe':
            log.info("Found Universe URL: {}".format(repo['uri']))
            return repo['uri']
    assert False, "Unable to find 'Universe' in list of repos: {}".format(repositories)


def _upgrade_or_downgrade(
        from_package_name,
        to_package_name,
        to_package_version,
        service_name,
        running_task_count,
        additional_options,
        timeout_seconds):
    task_ids = tasks.get_task_ids(service_name, '')
    if shakedown.dcos_version_less_than("1.10") or shakedown.ee_version() is None:
        # Marathon upgrade flow
        marathon.destroy_app(service_name)
        install.install(
            to_package_name,
            running_task_count,
            service_name=service_name,
            additional_options=additional_options,
            timeout_seconds=timeout_seconds,
            package_version=to_package_version)
    else:
        # CLI upgrade flow
        if from_package_name != to_package_name:
            # cosmos doesn't support upgrades across package names.
            # perform surgery on the running app's labels so that cosmos thinks the package names match.
            log.info('Renaming package in app {}: {} => {}'.format(service_name, from_package_name, to_package_name))
            new_config = _rename_package(marathon.get_config(service_name), to_package_name)
            marathon.update_app(service_name, new_config)
            plan.wait_for_completed_deployment(service_name)
        if additional_options:
            with tempfile.NamedTemporaryFile() as opts_f:
                opts_f.write(json.dumps(additional_options))
                cmd.run_cli('{} --name={} update start --package-version={} --options={}'.format(to_package_name, service_name, to_package_version, opts_f.name))
        else:
            cmd.run_cli('{} --name={} update start --package-version={}'.format(to_package_name, service_name, to_package_version))
    log.info('Checking that all tasks have restarted')
    tasks.check_tasks_updated(service_name, '', task_ids)


def _rename_package(marathon_app, desired_name):
    '''Updates the package name in the provided package.
    This hack allows us to test upgrades across package names, such as 'beta-kafka' v6 => 'kafka' stub-universe'''
    labels = marathon_app['labels']

    for expected_label in [PACKAGE_NAME_LABEL, PACKAGE_METADATA_LABEL, PACKAGE_DEF_LABEL]:
        if expected_label not in labels:
            raise 'Missing required label "{}" in marathon app: {}.'.format(expected_label, marathon_app['labels'])

    # PACKAGE_NAME
    log.info('Updating {}:'.format(PACKAGE_NAME_LABEL))
    log.info('  {} => {}'.format(labels[PACKAGE_NAME_LABEL], desired_name))
    labels[PACKAGE_NAME_LABEL] = desired_name

    # PACKAGE_METADATA
    # b64decode => update blob.name => b64encode
    log.info('Updating {}.{}:'.format(PACKAGE_METADATA_LABEL, 'name'))
    pkg_metadata = json.loads(base64.b64decode(labels[PACKAGE_METADATA_LABEL]).decode('utf-8'))
    log.info('  {} => {}'.format(pkg_metadata['name'], desired_name))
    pkg_metadata['name'] = desired_name
    labels[PACKAGE_METADATA_LABEL] = base64.b64encode(json.dumps(pkg_metadata).encode('utf-8')).decode('utf-8')

    # PACKAGE_DEFINITION
    # b64decode => get data field => b64decode => gunzip => set blob.name => gzip => b64encode => update data field => b64encode
    log.info('Updating {}.data.{}:'.format(PACKAGE_METADATA_LABEL, 'name'))

    pkg_def = json.loads(base64.b64decode(labels[PACKAGE_DEF_LABEL]).decode('utf-8'))
    use_gzip = pkg_def['metadata']['Content-Encoding'] == 'gzip'

    pkg_def_data_raw = base64.b64decode(pkg_def['data'])
    if use_gzip:
        pkg_def_data_raw = gzip.decompress(pkg_def_data_raw)
    pkg_def_data = json.loads(pkg_def_data_raw.decode('utf-8'))

    log.info('  {} => {}'.format(pkg_def_data['name'], desired_name))
    pkg_def_data['name'] = desired_name

    pkg_def_data_raw = json.dumps(pkg_def_data).encode('utf-8')
    if use_gzip:
        pkg_def_data_raw = gzip.compress(pkg_def_data_raw)
    pkg_def['data'] = base64.b64encode(pkg_def_data_raw).decode('utf-8')
    labels[PACKAGE_DEF_LABEL] = base64.b64encode(json.dumps(pkg_def).encode('utf-8')).decode('utf-8')

    marathon_app['labels'] = labels
    return marathon_app


def _get_pkg_version(package_name):
    return re.search(
        r'"version": "(\S+)"',
        cmd.run_cli('package describe {}'.format(package_name))).group(1)


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
