import json
import re
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_plan as plan
import sdk_spin as spin
import sdk_tasks as tasks


# (1) Installs Universe version of framework.
# (2) Upgrades to test version of framework.
# (3) Downgrades to Universe version.
# (4) Upgrades back to test version, as clean up.
def upgrade_downgrade(package_name, running_task_count):
    install.uninstall(package_name)

    test_version = get_pkg_version(package_name)
    print('Found test version: {}'.format(test_version))

    repositories = json.loads(cmd.run_cli('package repo list --json'))['repositories']
    print("Repositories: " + str(repositories))
    universe_url = "fail"
    for repo in repositories:
        if repo['name'] == 'Universe':
            universe_url = repo['uri']
            break

    assert "fail" != universe_url
    print("Universe URL: " + universe_url)

    # Move the Universe repo to the top of the repo list
    shakedown.remove_package_repo('Universe')
    add_repo('Universe', universe_url, test_version, 0, package_name)

    universe_version = get_pkg_version(package_name)
    print('Found Universe version: {}'.format(universe_version))

    print('Installing Universe version')
    install.install(package_name, running_task_count)

    # Move the Universe repo to the bottom of the repo list
    shakedown.remove_package_repo('Universe')
    add_last_repo('Universe', universe_url, universe_version, package_name)

    print('Upgrading to test version')
    upgrade_or_downgrade(package_name, running_task_count)

    # Move the Universe repo to the top of the repo list
    shakedown.remove_package_repo('Universe')
    add_repo('Universe', universe_url, test_version, 0, package_name)

    print('Downgrading to master version')
    upgrade_or_downgrade(package_name, running_task_count)

    # Move the Universe repo to the bottom of the repo list
    shakedown.remove_package_repo('Universe')
    add_last_repo('Universe', universe_url, universe_version, package_name)

    print('Upgrading to test version')
    upgrade_or_downgrade(package_name, running_task_count)


def upgrade_or_downgrade(package_name, running_task_count):
    task_ids = tasks.get_task_ids(package_name, '')
    marathon.destroy_app(package_name)
    install.install(package_name, running_task_count)
    print('Waiting for upgrade / downgrade deployment to complete')
    spin.time_wait_noisy(lambda: (
        plan.get_deployment_plan(package_name).json()['status'] == 'COMPLETE'))
    print('Checking that all tasks have restarted')
    tasks.check_tasks_updated(package_name, '', task_ids)


def get_test_repo_info():
    repos = shakedown.get_package_repos()
    test_repo = repos['repositories'][0]
    return test_repo['name'], test_repo['uri']


def get_pkg_version(package_name):
    pkg_description = cmd.run_cli('package describe {}'.format(package_name))
    regex = r'"version": "(\S+)"'
    match = re.search(regex, pkg_description)
    return match.group(1)


def add_repo(repo_name, repo_url, prev_version, index, package_name):
    assert shakedown.add_package_repo(
        repo_name,
        repo_url,
        index)
    # Make sure the new repo packages are available
    new_default_version_available(prev_version, package_name)


def add_last_repo(repo_name, repo_url, prev_version, package_name):
    assert shakedown.add_package_repo(
        repo_name,
        repo_url)
    # Make sure the new repo packages are available
    new_default_version_available(prev_version, package_name)


def new_default_version_available(prev_version, package_name):
    spin.time_wait_noisy(lambda: get_pkg_version(package_name) != prev_version)
