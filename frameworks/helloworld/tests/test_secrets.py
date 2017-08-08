import logging

import pytest

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_tasks
import sdk_marathon
import sdk_utils

import shakedown  # required by @sdk_utils.dcos_X_Y_or_higher


from retrying import retry
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

REQUIRED_SECRETS = ["secret1", "secret2", "secret3"]


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME)
        sdk_cmd.run_cli("package install --cli dcos-enterprise-cli")
        delete_secrets_all("{}/".format(PACKAGE_NAME))
        delete_secrets_all("{}/somePath/".format(PACKAGE_NAME))
        delete_secrets_all()

        yield  # let the test session execute
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
    update_secrets("{}/".format(PACKAGE_NAME), secret_content_alternative)

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


def _get_secret(name):
    log.info('Getting the value of secret %s', name)
    raw_output = sdk_cmd.run_cli("security secrets get {}".format(name),
                                 print_output=False,
                                 failure_is_fatal=False).strip()
    return raw_output.lstrip('value:').strip()


def _ensure_value(name, value):
    def fn():
        secret_output = _get_secret(name)
        if secret_output == value:
            return True
        else:
            log.info("Waiting for secret %s to be available", name)
            log.info("Got: %s (expected %s)", secret_output, value)
            return False

    shakedown.wait_for(fn, timeout_seconds=5 * 60)


def _create_secret(name, value):
    log.info("Creating secret: %s=%s", name, value)
    try:
        sdk_cmd.run_cli("security secrets create --value={} {}".format(value, name))
    except Exception as e:
        log.debug("Exception %s when creating secret.", e)

    _ensure_value(name, value)


def _update_secret(name, value):
    log.info("Updating secret to: %s=%s", name, value)
    try:
        sdk_cmd.run_cli("security secrets update --value={} {}".format(value, name))
    except Exception as e:
        log.debug("Exception %s when creating secret.", e)

    _ensure_value(name, value)


def _delete_secret(name):
    try:
        sdk_cmd.run_cli("security secrets delete {}".format(name))
    except Exception as e:
        log.debug("Exception %s when deleting secret.", e)

    _ensure_value(name, '')


def create_secrets(path_prefix="", secret_content_arg=secret_content_default):
    for s in REQUIRED_SECRETS:
        _create_secret("{}{}".format(path_prefix, s), secret_content_arg)


def update_secrets(path_prefix, secret_contents):
    for s in REQUIRED_SECRETS:
        _update_secret("{}{}".format(path_prefix, s), secret_contents)


def delete_secrets(path_prefix="", continue_on_failure=False):
    log.info("Deleting secrets: %s", REQUIRED_SECRETS)
    for s in REQUIRED_SECRETS:
        try:
            _delete_secret("{}{}".format(path_prefix, s))
        except Exception as e:
            if continue_on_failure:
                log.error("Failed to delete secret %s in cleanup: %s", s, e)
            else:
                raise(e)


def delete_secrets_all(path_prefix=""):
    log.info("Delete ALL secrets: %s", REQUIRED_SECRETS)
    delete_secrets(path_prefix, continue_on_failure=True)


@retry
def read_secret(task_name, command):
    cmd_str = "task exec {} {}".format(task_name, command)
    lines = sdk_cmd.run_cli(cmd_str).split('\n')
    log.info('dcos %s output: %s', cmd_str, lines)
    for i in lines:
        if i.strip().startswith(secret_content_default):
            return i
    raise Exception("Failed to read secret")
