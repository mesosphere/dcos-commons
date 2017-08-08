import logging

import pytest
from retrying import retry

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_tasks
import sdk_marathon
import sdk_utils

from tests.config import (
    PACKAGE_NAME
)

log = logging.getLogger(__name__)

NUM_HELLO = 2
NUM_WORLD = 3

secret_content_default = "hello-world-secret-data"
secret_content_alternative = secret_content_default + "-alternative"

secret_options = {
        "service": {
            "spec_file": "examples/secrets.yml"
        },
        "hello": {
            "count": NUM_HELLO,
            "secret1": "hello-world/secret1",
            "secret2": "hello-world/secret2"
        },
        "world": {
            "count": NUM_WORLD,
            "secret1": "hello-world/secret1",
            "secret2": "hello-world/secret2",
            "secret3": "hello-world/secret3"
        }
    }

options_dcos_space_test = {
    "service": {
        "spec_file": "examples/secrets.yml"
    },
    "hello": {
        "count": NUM_HELLO,
        "secret1": "hello-world/somePath/secret1",
        "secret2": "hello-world/somePath/secret2"
    },
    "world": {
        "count": NUM_WORLD,
        "secret1": "hello-world/somePath/secret1",
        "secret2": "hello-world/somePath/secret2",
        "secret3": "hello-world/somePath/secret3"
    }
}


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME)
        sdk_cmd.run_cli("package install --cli dcos-enterprise-cli")
        delete_secrets_all("{}/".format(PACKAGE_NAME))
        delete_secrets_all("{}/somePath/".format(PACKAGE_NAME))
        delete_secrets_all()

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME)
        delete_secrets_all("{}/".format(PACKAGE_NAME))
        delete_secrets_all("{}/somePath/".format(PACKAGE_NAME))
        delete_secrets_all()


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.secrets
@sdk_utils.dcos_1_10_or_higher
def test_secrets_basic():
    # 1) create Secrets
    # 2) install examples/secrets.yml
    # 3) if secret file is not created, tasks will fail
    # 4) wait till deployment finishes
    # 5) do replace operation
    # 6) ensure all tasks are running
    # 7) delete Secrets

    sdk_install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    sdk_install.install(PACKAGE_NAME, NUM_HELLO + NUM_WORLD, additional_options=secret_options)

    hello_tasks_0 = sdk_tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks_0 = sdk_tasks.get_task_ids(PACKAGE_NAME, "word-0")

    # ensure that secrets work after replace
    sdk_cmd.run_cli('hello-world pod replace hello-0')
    sdk_cmd.run_cli('hello-world pod replace world-0')

    sdk_tasks.check_tasks_updated(PACKAGE_NAME, "hello-0", hello_tasks_0)
    sdk_tasks.check_tasks_updated(PACKAGE_NAME, 'world-0', world_tasks_0)

    # tasks will fail if secret files are not created by mesos module
    sdk_tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.secrets
@sdk_utils.dcos_1_10_or_higher
def test_secrets_verify():
    # 1) create Secrets
    # 2) install examples/secrets.yml
    # 3) verify Secrets content
    # 4) delete Secrets

    sdk_install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    sdk_install.install(PACKAGE_NAME, NUM_HELLO + NUM_WORLD, additional_options=secret_options)

    # tasks will fail if secret file is not created
    sdk_tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    # Verify secret content, one from each pod type

    # get task id - only first pod
    hello_tasks = sdk_tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks = sdk_tasks.get_task_ids(PACKAGE_NAME, "world-0")


    # first secret: environment variable name is given in yaml
    assert secret_content_default == read_secret("world-0", "bash -c 'echo $WORLD_SECRET1_ENV'")

    # second secret: file path is given in yaml
    assert secret_content_default == read_secret("world-0", "cat WORLD_SECRET2_FILE")

    # third secret : no file path is given in yaml
    #            default file path is equal to secret path
    assert secret_content_default == read_secret("world-0", "cat hello-world/secret3")


    # hello tasks has container image, world tasks do not

    # first secret : environment variable name is given in yaml
    assert secret_content_default == read_secret("hello-0", "bash -c 'echo $HELLO_SECRET1_ENV'")

    # first secret : both environment variable name and file path are given in yaml
    assert secret_content_default == read_secret("hello-0", "cat HELLO_SECRET1_FILE")

    # second secret : file path is given in yaml
    assert secret_content_default == read_secret("hello-0", "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.secrets
@sdk_utils.dcos_1_10_or_higher
def test_secrets_update():
    # 1) create Secrets
    # 2) install examples/secrets.yml
    # 3) update Secrets
    # 4) restart task
    # 5) verify Secrets content (updated after restart)
    # 6) delete Secrets

    sdk_install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    sdk_install.install(PACKAGE_NAME, NUM_HELLO + NUM_WORLD, additional_options=secret_options)

    # tasks will fail if secret file is not created
    sdk_tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)


    sdk_cmd.run_cli("security secrets update --value={} {}/secret1".format(secret_content_alternative, PACKAGE_NAME))
    sdk_cmd.run_cli("security secrets update --value={} {}/secret2".format(secret_content_alternative, PACKAGE_NAME))
    sdk_cmd.run_cli("security secrets update --value={} {}/secret3".format(secret_content_alternative, PACKAGE_NAME))

    # Verify with hello-0 and world-0, just check with one of the pods

    hello_tasks_old = sdk_tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks_old = sdk_tasks.get_task_ids(PACKAGE_NAME, "world-0")

    # restart pods to retrieve new secret's content
    sdk_cmd.run_cli('hello-world pod restart hello-0')
    sdk_cmd.run_cli('hello-world pod restart world-0')

    # wait pod restart to complete
    sdk_tasks.check_tasks_updated(PACKAGE_NAME, "hello-0", hello_tasks_old)
    sdk_tasks.check_tasks_updated(PACKAGE_NAME, 'world-0', world_tasks_old)

    # wait till it is running
    sdk_tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    # get new task ids - only first pod
    hello_tasks = sdk_tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks = sdk_tasks.get_task_ids(PACKAGE_NAME, "world-0")

    # make sure content is changed
    assert secret_content_alternative == read_secret("world-0", "bash -c 'echo $WORLD_SECRET1_ENV'")
    assert secret_content_alternative == read_secret("world-0", "cat WORLD_SECRET2_FILE")
    assert secret_content_alternative == read_secret("world-0", "cat {}/secret3".format(PACKAGE_NAME))

    # make sure content is changed
    assert secret_content_alternative == read_secret("hello-0", "bash -c 'echo $HELLO_SECRET1_ENV'")
    assert secret_content_alternative == read_secret("hello-0", "cat HELLO_SECRET1_FILE")
    assert secret_content_alternative == read_secret("hello-0", "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity
@pytest.mark.secrets
@pytest.mark.smoke
@sdk_utils.dcos_1_10_or_higher
def test_secrets_config_update():
    # 1) install examples/secrets.yml
    # 2) create new Secrets, delete old Secrets
    # 2) update configuration with new Secrets
    # 4) verify secret content (using new Secrets after config update)

    sdk_install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    sdk_install.install(PACKAGE_NAME, NUM_HELLO + NUM_WORLD, additional_options=secret_options)

    # tasks will fail if secret file is not created
    sdk_tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    # Verify secret content, one from each pod type
    # get tasks ids - only first pod
    hello_tasks = sdk_tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks = sdk_tasks.get_task_ids(PACKAGE_NAME, "world-0")

    # make sure it has the default value
    assert secret_content_default == read_secret("world-0", "bash -c 'echo $WORLD_SECRET1_ENV'")
    assert secret_content_default == read_secret("world-0", "cat WORLD_SECRET2_FILE")
    assert secret_content_default == read_secret("world-0", "cat {}/secret3".format(PACKAGE_NAME))

    # hello tasks has container image
    assert secret_content_default == read_secret("hello-0", "bash -c 'echo $HELLO_SECRET1_ENV'")
    assert secret_content_default == read_secret("hello-0", "cat HELLO_SECRET1_FILE")
    assert secret_content_default == read_secret("hello-0", "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets (defaults)
    delete_secrets("{}/".format(PACKAGE_NAME))

    # create new secrets with new content -- New Value
    create_secrets(secret_content_arg=secret_content_alternative)

    config = sdk_marathon.get_config(PACKAGE_NAME)
    config['env']['HELLO_SECRET1'] = 'secret1'
    config['env']['HELLO_SECRET2'] = 'secret2'
    config['env']['WORLD_SECRET1'] = 'secret1'
    config['env']['WORLD_SECRET2'] = 'secret2'
    config['env']['WORLD_SECRET3'] = 'secret3'

    # config update
    sdk_marathon.update_app(PACKAGE_NAME, config)

    # wait till plan is complete - pods are supposed to restart
    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)

    # all tasks are running
    sdk_tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    # Verify secret content is changed

    # get task ids - only first pod
    hello_tasks = sdk_tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks = sdk_tasks.get_task_ids(PACKAGE_NAME, "world-0")

    assert secret_content_alternative == read_secret("world-0", "bash -c 'echo $WORLD_SECRET1_ENV'")
    assert secret_content_alternative == read_secret("world-0", "cat WORLD_SECRET2_FILE")
    assert secret_content_alternative == read_secret("world-0", "cat secret3")

    assert secret_content_alternative == read_secret("hello-0", "bash -c 'echo $HELLO_SECRET1_ENV'")
    assert secret_content_alternative == read_secret("hello-0", "cat HELLO_SECRET1_FILE")
    assert secret_content_alternative == read_secret("hello-0", "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets
    delete_secrets()


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.secrets
@sdk_utils.dcos_1_10_or_higher
def test_secrets_dcos_space():
    # 1) create secrets in hello-world/somePath, i.e. hello-world/somePath/secret1 ...
    # 2) Tasks with DCOS_SPACE hello-world/somePath
    #       or some DCOS_SPACE path under hello-world/somePath
    #               (for example hello-world/somePath/anotherPath/)
    #    can access these Secrets

    sdk_install.uninstall(PACKAGE_NAME)

    # cannot access these secrets because of DCOS_SPACE authorization
    create_secrets("{}/somePath/".format(PACKAGE_NAME))

    try:
        sdk_install.install(
            PACKAGE_NAME,
            NUM_HELLO + NUM_WORLD,
            additional_options=options_dcos_space_test,
            timeout_seconds=5 * 60) # Wait for 5 minutes. We don't need to wait 15 minutes for hello-world to fail an install

        assert False, "Should have failed to install"

    except AssertionError as arg:
        raise arg

    except:
        pass  # expected to fail

    # clean up and delete secrets
    delete_secrets("{}/somePath/".format(PACKAGE_NAME))


def create_secrets(path_prefix="", secret_content_arg=secret_content_default):
    sdk_cmd.run_cli("security secrets create --value={} {}secret1".format(secret_content_arg, path_prefix))
    sdk_cmd.run_cli("security secrets create --value={} {}secret2".format(secret_content_arg, path_prefix))
    sdk_cmd.run_cli("security secrets create --value={} {}secret3".format(secret_content_arg, path_prefix))


def delete_secrets(path_prefix=""):
    sdk_cmd.run_cli("security secrets delete {}secret1".format(path_prefix))
    sdk_cmd.run_cli("security secrets delete {}secret2".format(path_prefix))
    sdk_cmd.run_cli("security secrets delete {}secret3".format(path_prefix))


def delete_secrets_all(path_prefix=""):
    # if there is any secret left, delete
    # use in teardown_module
    try:
        sdk_cmd.run_cli("security secrets get {}secret1".format(path_prefix))
        sdk_cmd.run_cli("security secrets delete {}secret1".format(path_prefix))
    except:
        pass
    try:
        sdk_cmd.run_cli("security secrets get {}secret2".format(path_prefix))
        sdk_cmd.run_cli("security secrets delete {}secret2".format(path_prefix))
    except:
        pass
    try:
        sdk_cmd.run_cli("security secrets get {}secret3".format(path_prefix))
        sdk_cmd.run_cli("security secrets delete {}secret3".format(path_prefix))
    except:
        pass


@retry
def read_secret(task_name, command):
    cmd_str = "task exec {} {}".format(task_name, command)
    lines = sdk_cmd.run_cli(cmd_str).split('\n')
    log.info('dcos %s output: %s', cmd_str, lines)
    for i in lines:
        if i.strip().startswith(secret_content_default):
            return i
    raise Exception("Failed to read secret")
