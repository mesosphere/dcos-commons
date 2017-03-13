import pytest

import json
import shakedown
import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_upgrade as upgrade


from tests.config import (
    PACKAGE_NAME,
    DEFAULT_BROKER_COUNT,
    POD_TYPE,
    SERVICE_NAME
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()


def teardown_module(module):
    install.uninstall(SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
def test_upgrade():

    test_version = upgrade.get_pkg_version(PACKAGE_NAME)
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
    add_repo('Universe', universe_url, test_version, 0, PACKAGE_NAME)

    universe_version = upgrade.get_pkg_version(PACKAGE_NAME)
    print('Found Universe version: {}'.format(universe_version))

    print('Installing Universe version')
    install.install(PACKAGE_NAME, DEFAULT_BROKER_COUNT)

    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)
    broker_ids = tasks.get_task_ids(SERVICE_NAME, '{}-'.format(POD_TYPE))

    # Move the Universe repo to the bottom of the repo list
    shakedown.remove_package_repo('Universe')
    upgrade.add_last_repo('Universe', universe_url, universe_version, PACKAGE_NAME)

    print('Upgrading to test version')
    marathon.destroy_app(SERVICE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_BROKER_COUNT)

    tasks.check_tasks_updated(SERVICE_NAME, '{}-'.format(POD_TYPE), broker_ids)
    # all tasks are running
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

