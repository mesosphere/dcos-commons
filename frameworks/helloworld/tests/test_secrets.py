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

secret_content = "hello-world-secret-data"
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


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)
    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))
    # clean up and delete secrets
    delete_secrets()


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


@pytest.mark.sanity
@pytest.mark.secrets
def test_secrets_verify():
    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))
    install.install(PACKAGE_NAME, num_private_agents * 2, additional_options=secret_options)
    # launch will fail if secrets are not available or not accessible
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    # tasks will fail if secret file is not created
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2)

    # verify secret content, one from each pod type
    hello_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    # hello tasks has container image
    assert secret_content == cmd.run_cli("dcos task exec 'echo $HELLO_SECRET1_ENV' {}".format(hello_tasks_0))
    assert secret_content == cmd.run_cli("dcos task exec 'cat HELLO_SECRET1_FILE' {}".format(hello_tasks_0))
    assert secret_content == cmd.run_cli("dcos task exec 'cat HELLO_SECRET2_FILE' {}".format(hello_tasks_0))

    world_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")
    assert secret_content == cmd.run_cli("dcos task exec 'echo $WORLD_SECRET1_ENV' {}".format(world_tasks_0))
    assert secret_content == cmd.run_cli("dcos task exec 'cat WORLD_SECRET2_FILE' {}".format(world_tasks_0))
    assert secret_content == cmd.run_cli("dcos task exec 'cat hello-word/secret3' {}".format(world_tasks_0))

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity
@pytest.mark.secrets
def test_secrets_update():
    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))
    install.install(PACKAGE_NAME, num_private_agents * 2, additional_options=secret_options)
    # launch will fail if secrets are not available or not accessible
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    # tasks will fail if secret file is not created
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2)

    cmd.run_cli("dcos security secrets update --value={} {}/secret1".format(secret_content_alternative, PACKAGE_NAME))
    cmd.run_cli("dcos security secrets update --value={} {}/secret2".format(secret_content_alternative, PACKAGE_NAME))
    cmd.run_cli("dcos security secrets update --value={} {}/secret3".format(secret_content_alternative, PACKAGE_NAME))

    # replace pods to retreive new secret content
    cmd.run_cli('hello-world pods replace hello-0')
    cmd.run_cli('hello-world pods replace world-0')

    hello_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'echo $HELLO_SECRET1_ENV' {}".format(hello_tasks_0))
    assert secret_content_alternative == cmd.run_cli("dcos task exec 'cat HELLO_SECRET1_FILE' {}".format(hello_tasks_0))
    assert secret_content_alternative == cmd.run_cli("dcos task exec 'cat HELLO_SECRET2_FILE' {}".format(hello_tasks_0))

    world_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")
    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'echo $WORLD_SECRET1_ENV' {}".format(world_tasks_0))
    assert secret_content_alternative == cmd.run_cli("dcos task exec 'cat WORLD_SECRET2_FILE' {}".format(world_tasks_0))
    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'cat {}/secret3' {}".format(PACKAGE_NAME, world_tasks_0))

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity
@pytest.mark.secrets
def test_secrets_config_update():
    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))
    install.install(PACKAGE_NAME, num_private_agents * 2, additional_options=secret_options)
    # launch will fail if secrets are not available or not accessible
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    # tasks will fail if secret file is not created
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2)

    # verify secret content, one from each pod type
    hello_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    # hello tasks has container image
    assert secret_content == cmd.run_cli("dcos task exec 'echo $HELLO_SECRET1_ENV' {}".format(hello_tasks_0))
    assert secret_content == cmd.run_cli("dcos task exec 'cat HELLO_SECRET1_FILE' {}".format(hello_tasks_0))
    assert secret_content == cmd.run_cli("dcos task exec 'cat HELLO_SECRET2_FILE' {}".format(hello_tasks_0))

    world_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")
    assert secret_content == cmd.run_cli("dcos task exec 'echo $WORLD_SECRET1_ENV' {}".format(world_tasks_0))
    assert secret_content == cmd.run_cli("dcos task exec 'cat WORLD_SECRET2_FILE' {}".format(world_tasks_0))
    assert secret_content == cmd.run_cli("dcos task exec 'cat secret3' {}".format(world_tasks_0))

    # clean up and delete secrets (defaults)
    delete_secrets("{}/".format(PACKAGE_NAME))
    # create new secrets
    create_secrets()

    config = marathon.get_config(PACKAGE_NAME)
    config['env']['HELLO_SECRET1'] = 'secret1'
    config['env']['HELLO_SECRET2'] = 'secret2'
    config['env']['WORLD_SECRET1'] = 'secret1'
    config['env']['WORLD_SECRET2'] = 'secret2'
    config['env']['WORLD_SECRET3'] = 'secret3'
    marathon.update_app(PACKAGE_NAME, config)
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2)

    hello_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'echo $HELLO_SECRET1_ENV' {}".format(hello_tasks_0))
    assert secret_content_alternative == cmd.run_cli("dcos task exec 'cat HELLO_SECRET1_FILE' {}".format(hello_tasks_0))
    assert secret_content_alternative == cmd.run_cli("dcos task exec 'cat HELLO_SECRET2_FILE' {}".format(hello_tasks_0))

    world_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")
    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'echo $WORLD_SECRET1_ENV' {}".format(world_tasks_0))
    assert secret_content_alternative == cmd.run_cli("dcos task exec 'cat WORLD_SECRET2_FILE' {}".format(world_tasks_0))
    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'cat {}/secret3' {}".format(PACKAGE_NAME, world_tasks_0))

    # Now, an additional hello-server task will launch
    # where the _new_ constraint will tell it to be.
    tasks.check_running(PACKAGE_NAME, 2)
    cmd.run_cli("dcos security secrets update --value={} {}/secret1".format(secret_content_alternative, PACKAGE_NAME))
    cmd.run_cli("dcos security secrets update --value={} {}/secret2".format(secret_content_alternative, PACKAGE_NAME))
    cmd.run_cli("dcos security secrets update --value={} {}/secret3".format(secret_content_alternative, PACKAGE_NAME))

    # replace pods to retreive new secret content
    cmd.run_cli('hello-world pods replace hello-0')
    cmd.run_cli('hello-world pods replace world-0')

    hello_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'echo $HELLO_SECRET1_ENV' {}".format(hello_tasks_0))
    assert secret_content_alternative == cmd.run_cli("dcos task exec 'cat HELLO_SECRET1_FILE' {}".format(hello_tasks_0))
    assert secret_content_alternative == cmd.run_cli("dcos task exec 'cat HELLO_SECRET2_FILE' {}".format(hello_tasks_0))

    world_tasks_0 = tasks.get_task_ids(PACKAGE_NAME, "word-0")
    assert secret_content_alternative == cmd.run_cli(
        "dcos task exec 'echo $WORLD_SECRET1_ENV' {}".format(world_tasks_0))
    assert secret_content_alternative == cmd.run_cli("dcos task exec 'cat WORLD_SECRET2_FILE' {}".format(world_tasks_0))
    assert secret_content_alternative == cmd.run_cli("dcos task exec 'cat secret3' {}".format(world_tasks_0))

    # clean up and delete secrets
    delete_secrets()

ecret_options = {
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


@pytest.mark.sanity
@pytest.mark.secrets
def test_secrets_dcos_space():
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

    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/somePath/".format(PACKAGE_NAME))
    install.install(PACKAGE_NAME, num_private_agents * 2, additional_options=secret_options)

    try:
        plan.wait_for_completed_deployment(PACKAGE_NAME)
        assert False, "Should have failed to install"
    except AssertionError as arg:
        raise arg
    except:
        pass  # expected to fail

    # clean up and delete secrets
    delete_secrets("{}/somePath/".format(PACKAGE_NAME))


def create_secrets(path_prefix=""):
    cmd.run_cli("package install --cli dcos-enterprise-cli ")
    cmd.run_cli("security secrets create --value={} {}secret1".format(secret_content, path_prefix))
    cmd.run_cli("security secrets create --value={} {}secret2".format(secret_content, path_prefix))
    cmd.run_cli("security secrets create --value={} {}secret3".format(secret_content, path_prefix))


def delete_secrets(path_prefix=""):
    cmd.run_cli("security secrets delete {}secret1".format(path_prefix))
    cmd.run_cli("security secrets delete {}secret2".format(path_prefix))
    cmd.run_cli("security secrets delete {}secret3".format(path_prefix))

