import pytest

import json
import shakedown
import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_test_upgrade as upgrade
import sdk_utils as utils
import sdk_tasks as tasks

from tests.test_utils import (
    PACKAGE_NAME,
    DEFAULT_BROKER_COUNT,
    DEFAULT_POD_TYPE,
    SERVICE_NAME,
    DEFAULT_TASK_NAME,
    service_cli
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
    utils.out('Found test version: {}'.format(test_version))

    repositories = json.loads(cmd.run_cli('package repo list --json'))['repositories']
    utils.out("Repositories: " + str(repositories))

    if len(repositories) < 2:
        utils.out("There is only one version in the repository. Skipping upgrade test!")
        assert repo[0]['name'] == 'Universe'
        return

    test_repo_name, test_repo_url = upgrade.get_test_repo_info();

    for repo in repositories:
        if repo['name'] != 'Universe':
            shakedown.remove_package_repo(repo['name'])

    universe_version = upgrade.get_pkg_version(PACKAGE_NAME)
    utils.out('Found Universe version: {}'.format(universe_version))

    utils.out('Installing Universe version: {}'.format(universe_version))
    install.install(PACKAGE_NAME, DEFAULT_BROKER_COUNT)
    utils.out('Installation complete for Universe version: {}'.format(universe_version))

    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)
    broker_ids = tasks.get_task_ids(SERVICE_NAME, 'broker-')

    utils.out('Adding test version to repository with name: {} and url: {}'.format(test_repo_name, test_repo_url))
    upgrade.add_repo(test_repo_name, test_repo_url, universe_version, 0, PACKAGE_NAME)

    utils.out('Upgrading to test version: {}'.format(test_version))
    marathon.destroy_app(SERVICE_NAME)

    utils.out('Installing test version: {}'.format(test_version))

    # installation will return with old tasks because they are still running
    install.install(PACKAGE_NAME, DEFAULT_BROKER_COUNT)
    utils.out('Installation complete for test version: {}'.format(test_version))

    # wait till tasks are restarted
    tasks.check_tasks_updated(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE), broker_ids)
    utils.out('All task are restarted')
    # all tasks are running
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)
     
    address = service_cli('endpoints {}'.format(DEFAULT_TASK_NAME))
    assert len(address) == 4
    assert len(address['dns']) == DEFAULT_BROKER_COUNT
    assert len(address['address']) == DEFAULT_BROKER_COUNT

