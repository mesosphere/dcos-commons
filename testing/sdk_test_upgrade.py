import json
import re
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_plan as plan
import sdk_tasks as tasks
import sdk_utils


# (1) Installs Universe version of framework.
# (2) Upgrades to test version of framework.
# (3) Downgrades to Universe version.
# (4) Upgrades back to test version, as clean up (if reinstall_test_version == True).
#
# With beta packages, the Universe package name is different from the test package name.
# We install both with the same service name=test_package_name.
def upgrade_downgrade(
        universe_package_name,
        test_package_name,
        running_task_count,
        additional_options={},
        reinstall_test_version=True):
    install.uninstall(test_package_name)

    test_version = get_pkg_version(test_package_name)
    sdk_utils.out('Found test version: {}'.format(test_version))

    repositories = json.loads(cmd.run_cli('package repo list --json'))['repositories']
    sdk_utils.out("Repositories: " + str(repositories))
    universe_url = "fail"
    for repo in repositories:
        if repo['name'] == 'Universe':
            universe_url = repo['uri']
            break

    assert "fail" != universe_url
    sdk_utils.out("Universe URL: " + universe_url)

    # Move the Universe repo to the top of the repo list
    shakedown.remove_package_repo('Universe')
    add_repo('Universe', universe_url, test_version, 0, universe_package_name)

    universe_version = get_pkg_version(universe_package_name)
    sdk_utils.out('Found Universe version: {}'.format(universe_version))

    sdk_utils.out('Installing Universe version')
    # Keep the service name the same throughout the test
    install.install(universe_package_name, running_task_count, service_name=test_package_name,
                    check_suppression=False, additional_options=additional_options)

    # Move the Universe repo to the bottom of the repo list
    shakedown.remove_package_repo('Universe')
    add_last_repo('Universe', universe_url, universe_version, test_package_name)

    sdk_utils.out('Upgrading to test version')
    upgrade_or_downgrade(test_package_name, test_package_name, running_task_count, additional_options)

    # Move the Universe repo to the top of the repo list
    shakedown.remove_package_repo('Universe')
    add_repo('Universe', universe_url, test_version, 0, universe_package_name)

    sdk_utils.out('Downgrading to Universe version')
    upgrade_or_downgrade(universe_package_name, test_package_name, running_task_count, additional_options)

    # Move the Universe repo to the bottom of the repo list
    shakedown.remove_package_repo('Universe')
    add_last_repo('Universe', universe_url, universe_version, test_package_name)

    if reinstall_test_version:
        sdk_utils.out('Re-upgrading to test version before exiting')
        upgrade_or_downgrade(test_package_name, test_package_name, running_task_count, additional_options)
    else:
        sdk_utils.out('Skipping reinstall of test version, uninstalling universe version')
        install.uninstall(test_package_name, package_name=universe_package_name)


# In the soak cluster, we assume that the Universe version of the framework is already installed.
# Also, we assume that the Universe is the default repo (at --index=0) and the stub repos are already in place,
# so we don't need to add or remove any repos.
#
# (1) Upgrades to test version of framework.
# (2) Downgrades to Universe version.
def soak_upgrade_downgrade(universe_package_name, test_package_name, service_name, running_task_count,
                           install_options={}):
    print('Upgrading to test version')
    upgrade_or_downgrade(test_package_name, service_name, running_task_count, install_options, 'stub-universe')

    print('Downgrading to Universe version')
    # Default Universe is at --index=0
    upgrade_or_downgrade(universe_package_name, service_name, running_task_count, install_options)


def upgrade_or_downgrade(package_name, service_name, running_task_count, additional_options, package_version=None):
    task_ids = tasks.get_task_ids(service_name, '')
    marathon.destroy_app(service_name)
    install.install(
        package_name,
        running_task_count,
        service_name=service_name,
        additional_options=additional_options,
        package_version=package_version,
        check_suppression=False)
    sdk_utils.out('Waiting for upgrade / downgrade deployment to complete')
    plan.wait_for_completed_deployment(service_name)
    sdk_utils.out('Checking that all tasks have restarted')
    tasks.check_tasks_updated(service_name, '', task_ids)


def get_test_repo_info():
    repos = shakedown.get_package_repos()
    test_repo = repos['repositories'][0]
    return test_repo['name'], test_repo['uri']


def get_pkg_version(package_name):
    pkg_description = cmd.run_cli('package describe {}'.format(package_name))
    regex = r'"version": "(\S+)"'
    match = re.search(regex, pkg_description)
    return match.group(1)


# Default repo is the one at index=0.
def add_repo(repo_name, repo_url, prev_version, index, default_repo_package_name):
    assert shakedown.add_package_repo(
        repo_name,
        repo_url,
        index)
    # Make sure the new default repo packages are available
    new_default_version_available(prev_version, default_repo_package_name)


def add_last_repo(repo_name, repo_url, prev_version, default_repo_package_name):
    assert shakedown.add_package_repo(
        repo_name,
        repo_url)
    # Make sure the new default repo packages are available
    new_default_version_available(prev_version, default_repo_package_name)


def new_default_version_available(prev_version, default_repo_package_name):
    shakedown.wait_for(lambda: get_pkg_version(default_repo_package_name) != prev_version, noisy=True)
