import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan
import sdk_tasks as tasks
import sdk_marathon as marathon
import time
import json

from tests.config import (
    PACKAGE_NAME
)

num_private_agents = len(shakedown.get_private_agents())
num_private_agents = 1

secret_content_default = "hello-world-secret-data"
secret_content_alternative = "hello-world-secret-data-alternative"

secret_options = {
        "service": {
            "spec_file": "examples/secrets.yml"
        },
        "hello": {
            "count": num_private_agents,
            "secret1": "hello-world/secret1",
            "secret2": "hello-world/secret2"
        },
        "world": {
            "count": num_private_agents,
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
        "count": num_private_agents,
        "secret1": "hello-world/somePath/secret1",
        "secret2": "hello-world/somePath/secret2"
    },
    "world": {
        "count": num_private_agents,
        "secret1": "hello-world/somePath/secret1",
        "secret2": "hello-world/somePath/secret2",
        "secret3": "hello-world/somePath/secret3"
    }
}


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)
    delete_secrets_all("{}/".format(PACKAGE_NAME))
    delete_secrets_all("{}/somePath/".format(PACKAGE_NAME))
    delete_secrets_all()


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.secrets
def test_secrets_basic():
    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    install.install(PACKAGE_NAME, num_private_agents * 2, additional_options=secret_options)

    # default is serial strategy, hello deploys first
    # launch will fail if secrets are not available or not accessible
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    hello_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")

    # ensure that secrets work after replace
    cmd.run_cli('hello-world pods replace hello-0')
    cmd.run_cli('hello-world pods replace world-0')

    tasks.check_tasks_updated(PACKAGE_NAME, "hello-0", hello_tasks_0)
    tasks.check_tasks_updated(PACKAGE_NAME, 'world-0', world_tasks_0)

    # tasks will fail if secret files are not created by mesos module
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2)

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity0
@pytest.mark.secrets0
def test_secrets_verify():
    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    install.install(PACKAGE_NAME, num_private_agents * 2, additional_options=secret_options)
    world_task_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")

    print(world_task_0)
    # launch will fail if secrets are not available or not accessible
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    # tasks will fail if secret file is not created
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2)

    # Verify secret content, one from each pod type

    # get task id
    hello_task_0 = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_task_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")

    print(world_task_0)
    assert secret_content_default == cmd.run_cli("task exec {} 'echo $WORLD_SECRET1_ENV' ".format(world_task_0))
    assert secret_content_default == cmd.run_cli("task exec {} 'cat WORLD_SECRET2_FILE' ".format(world_task_0))
    assert secret_content_default == cmd.run_cli("task exec {} 'cat hello-word/secret3' ".format(world_task_0))

    # hello tasks has container image
    assert secret_content_default == cmd.run_cli("task exec {} 'echo $HELLO_SECRET1_ENV' ".format(hello_task_0))
    assert secret_content_default == cmd.run_cli("task exec {} 'cat HELLO_SECRET1_FILE' ".format(hello_task_0))
    assert secret_content_default == cmd.run_cli("task exec {} 'cat HELLO_SECRET2_FILE' ".format(hello_task_0))

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity0
@pytest.mark.secrets
def test_secrets_update():
    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    install.install(PACKAGE_NAME, num_private_agents * 2, additional_options=secret_options)

    # launch will fail if secrets are not available or not accessible
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    # tasks will fail if secret file is not created
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2)

    cmd.run_cli("security secrets update --value={} {}/secret1".format(secret_content_alternative, PACKAGE_NAME))
    cmd.run_cli("security secrets update --value={} {}/secret2".format(secret_content_alternative, PACKAGE_NAME))
    cmd.run_cli("security secrets update --value={} {}/secret3".format(secret_content_alternative, PACKAGE_NAME))

    # Verify with hello-0 and world-0, just check with one of the pods

    # replace pods to retrieve new secret's content
    cmd.run_cli('hello-world pods replace hello-0')
    cmd.run_cli('hello-world pods replace world-0')

    # get task id
    world_task_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")

    # make sure content is changed
    assert secret_content_alternative == cmd.run_cli(
        "task exec 'echo $WORLD_SECRET1_ENV' {}".format(world_task_0[0]))
    assert secret_content_alternative == cmd.run_cli("task exec 'cat WORLD_SECRET2_FILE' {}".format(world_task_0[0]))
    assert secret_content_alternative == cmd.run_cli(
        "task exec 'cat {}/secret3' {}".format(PACKAGE_NAME, world_task_0[0]))

    # get task id
    hello_task_0 = tasks.get_task_ids(PACKAGE_NAME, "hello-0")

    # make sure content is changed
    assert secret_content_alternative == cmd.run_cli(
        "task exec 'echo $HELLO_SECRET1_ENV' {}".format(hello_task_0[0]))
    assert secret_content_alternative == cmd.run_cli("task exec {} 'cat HELLO_SECRET1_FILE' ".format(hello_task_0[0]))
    assert secret_content_alternative == cmd.run_cli("task exec {} 'cat HELLO_SECRET2_FILE' ".format(hello_task_0[0]))

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity0
@pytest.mark.secrets
def test_secrets_config_update():
    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    install.install(PACKAGE_NAME, num_private_agents * 2, additional_options=secret_options)

    # launch will fail if secrets are not available or not accessible
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    # tasks will fail if secret file is not created
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2)

    # Verify secret content, one from each pod type
    # get tasks id
    hello_task_0 = tasks.get_task_ids(PACKAGE_NAME, "hello-0")

    # hello tasks has container image
    assert secret_content_default == cmd.run_cli("task exec 'echo $HELLO_SECRET1_ENV' {}".format(hello_task_0))
    assert secret_content_default == cmd.run_cli("task exec 'cat HELLO_SECRET1_FILE' {}".format(hello_task_0))
    assert secret_content_default == cmd.run_cli("task exec 'cat HELLO_SECRET2_FILE' {}".format(hello_task_0))

    world_task_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")
    # make sure it has the default value
    assert secret_content_default == cmd.run_cli("task exec 'echo $WORLD_SECRET1_ENV' {}".format(world_task_0))
    assert secret_content_default == cmd.run_cli("task exec 'cat WORLD_SECRET2_FILE' {}".format(world_task_0))
    assert secret_content_default == cmd.run_cli("task exec 'cat secret3' {}".format(world_task_0))

    # clean up and delete secrets (defaults)
    delete_secrets("{}/".format(PACKAGE_NAME))

    # create new secrets with new content -- New Value
    create_secrets(secret_content_arg=secret_content_alternative)

    config = marathon.get_config(PACKAGE_NAME)
    config['env']['HELLO_SECRET1'] = 'secret1'
    config['env']['HELLO_SECRET2'] = 'secret2'
    config['env']['WORLD_SECRET1'] = 'secret1'
    config['env']['WORLD_SECRET2'] = 'secret2'
    config['env']['WORLD_SECRET3'] = 'secret3'

    # config update
    marathon.update_app(PACKAGE_NAME, config)

    # wait till plan is complete - pods are supposed to restart
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    # all tasks are running
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2)

    # Verify secret content is changed
    # get task id
    hello_task_0 = tasks.get_task_ids(PACKAGE_NAME, "hello-0")

    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'echo $HELLO_SECRET1_ENV' {}".format(hello_task_0))
    assert secret_content_alternative == cmd.run_cli("task exec 'cat HELLO_SECRET1_FILE' {}".format(hello_task_0))
    assert secret_content_alternative == cmd.run_cli("task exec 'cat HELLO_SECRET2_FILE' {}".format(hello_task_0))

    # get task id
    world_task_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")

    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'echo $WORLD_SECRET1_ENV' {}".format(world_task_0))
    assert secret_content_alternative == cmd.run_cli("task exec 'cat WORLD_SECRET2_FILE' {}".format(world_task_0))
    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'cat secret3' {}".format(world_task_0))

    # clean up and delete secrets
    delete_secrets()


@pytest.mark.sanity0
@pytest.mark.secrets
def test_secrets_dcos_space():

    install.uninstall(PACKAGE_NAME)

    # dcos_space authorization, can not access these secrets
    create_secrets("{}/somePath/".format(PACKAGE_NAME))

    install.install(PACKAGE_NAME, num_private_agents * 2, additional_options=options_dcos_space_test)

    try:
        plan.wait_for_completed_deployment(PACKAGE_NAME)
        assert False, "Should have failed to install"
    except AssertionError as arg:
        raise arg
    except:
        pass  # expected to fail

    # clean up and delete secrets
    delete_secrets("{}/somePath/".format(PACKAGE_NAME))


def create_secrets(path_prefix="", secret_content_arg=secret_content_default):
    cmd.run_cli("package install --cli dcos-enterprise-cli ")
    cmd.run_cli("security secrets create --value={} {}secret1".format(secret_content_arg, path_prefix))
    cmd.run_cli("security secrets create --value={} {}secret2".format(secret_content_arg, path_prefix))
    cmd.run_cli("security secrets create --value={} {}secret3".format(secret_content_arg, path_prefix))


def delete_secrets(path_prefix=""):
    cmd.run_cli("security secrets delete {}secret1".format(path_prefix))
    cmd.run_cli("security secrets delete {}secret2".format(path_prefix))
    cmd.run_cli("security secrets delete {}secret3".format(path_prefix))


def delete_secrets_all(path_prefix=""):
    # if there is any secret left, delete
    # use in teardown_module
    try:
        cmd.run_cli("security secrets get {}secret1".format(path_prefix))
        cmd.run_cli("security secrets delete {}secret1".format(path_prefix))
    except:
        pass
    try:
        cmd.run_cli("security secrets get {}secret2".format(path_prefix))
        cmd.run_cli("security secrets delete {}secret2".format(path_prefix))
    except:
        pass
    try:
        cmd.run_cli("security secrets get {}secret3".format(path_prefix))
        cmd.run_cli("security secrets delete {}secret3".format(path_prefix))
    except:
        pass

