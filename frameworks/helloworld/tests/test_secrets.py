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

NUM_HELLO = 2
NUM_WORLD = 3

secret_content_default = "hello-world-secret-data"
secret_content_alternative = "hello-world-secret-data-alternative"

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


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    cmd.run_cli("package install --cli dcos-enterprise-cli")
    delete_secrets_all("{}/".format(PACKAGE_NAME))
    delete_secrets_all("{}/somePath/".format(PACKAGE_NAME))
    delete_secrets_all()


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)
    delete_secrets_all("{}/".format(PACKAGE_NAME))
    delete_secrets_all("{}/somePath/".format(PACKAGE_NAME))
    delete_secrets_all()


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.secrets
def test_secrets_basic():
    # 1) create Secrets
    # 2) install examples/secrets.yml
    # 3) if secret file is not created, tasks will fail
    # 4) wait till deployment finishes
    # 5) do replace operation
    # 6) ensure all tasks are running
    # 7) delete Secrets

    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    install.install(PACKAGE_NAME, NUM_HELLO + NUM_WORLD, additional_options=secret_options)

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
    tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity
@pytest.mark.secrets
def test_secrets_verify():
    # 1) create Secrets
    # 2) install examples/secrets.yml
    # 3) verify Secrets content
    # 4) delete Secrets

    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    install.install(PACKAGE_NAME, NUM_HELLO + NUM_WORLD, additional_options=secret_options)

    # launch will fail if secrets are not available or not accessible
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    # tasks will fail if secret file is not created
    tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    # Verify secret content, one from each pod type

    # get task id - only first pod
    hello_tasks = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks = tasks.get_task_ids(PACKAGE_NAME, "world-0")


    # first secret: environment variable name is given in yaml
    assert secret_content_default == task_exec(world_tasks[0], "bash -c 'echo $WORLD_SECRET1_ENV'")

    # second secret: file path is given in yaml
    assert secret_content_default == task_exec(world_tasks[0], "cat WORLD_SECRET2_FILE")

    # third secret : no file path is given in yaml
    #            default file path is equal to secret path
    assert secret_content_default == task_exec(world_tasks[0], "cat hello-world/secret3")


    # hello tasks has container image, world tasks do not

    # first secret : environment variable name is given in yaml
    assert secret_content_default == task_exec(hello_tasks[0], "bash -c 'echo $HELLO_SECRET1_ENV'")

    # first secret : both environment variable name and file path are given in yaml
    assert secret_content_default == task_exec(hello_tasks[0], "cat HELLO_SECRET1_FILE")

    # second secret : file path is given in yaml
    assert secret_content_default == task_exec(hello_tasks[0], "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity
@pytest.mark.secrets
def test_secrets_update():
    # 1) create Secrets
    # 2) install examples/secrets.yml
    # 3) update Secrets
    # 4) restart task
    # 5) verify Secrets content (updated after restart)
    # 6) delete Secrets

    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    install.install(PACKAGE_NAME, NUM_HELLO + NUM_WORLD, additional_options=secret_options)

    # launch will fail if secrets are not available or not accessible
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    # tasks will fail if secret file is not created
    tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    cmd.run_cli("security secrets update --value={} {}/secret1".format(secret_content_alternative, PACKAGE_NAME))
    cmd.run_cli("security secrets update --value={} {}/secret2".format(secret_content_alternative, PACKAGE_NAME))
    cmd.run_cli("security secrets update --value={} {}/secret3".format(secret_content_alternative, PACKAGE_NAME))

    # Verify with hello-0 and world-0, just check with one of the pods

    hello_tasks_old = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks_old = tasks.get_task_ids(PACKAGE_NAME, "world-0")

    # restart pods to retrieve new secret's content
    cmd.run_cli('hello-world pods restart hello-0')
    cmd.run_cli('hello-world pods restart world-0')

    # wait pod restart to complete
    tasks.check_tasks_updated(PACKAGE_NAME, "hello-0", hello_tasks_old)
    tasks.check_tasks_updated(PACKAGE_NAME, 'world-0', world_tasks_old)

    # wait till it is running
    tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    # get new task ids - only first pod
    hello_tasks = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks = tasks.get_task_ids(PACKAGE_NAME, "world-0")

    # make sure content is changed
    assert secret_content_alternative == task_exec(world_tasks[0], "bash -c 'echo $WORLD_SECRET1_ENV'")
    assert secret_content_alternative == task_exec(world_tasks[0], "cat WORLD_SECRET2_FILE")
    assert secret_content_alternative == task_exec(world_tasks[0], "cat {}/secret3".format(PACKAGE_NAME))

    # make sure content is changed
    assert secret_content_alternative == task_exec(hello_tasks[0], "bash -c 'echo $HELLO_SECRET1_ENV'")
    assert secret_content_alternative == task_exec(hello_tasks[0], "cat HELLO_SECRET1_FILE")
    assert secret_content_alternative == task_exec(hello_tasks[0], "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets
    delete_secrets("{}/".format(PACKAGE_NAME))


@pytest.mark.sanity
@pytest.mark.secrets
def test_secrets_config_update():
    # 1) install examples/secrets.yml
    # 2) create new Secrets, delete old Secrets
    # 2) update configuration with new Secrets
    # 4) verify secret content (using new Secrets after config update)

    install.uninstall(PACKAGE_NAME)

    create_secrets("{}/".format(PACKAGE_NAME))

    install.install(PACKAGE_NAME, NUM_HELLO + NUM_WORLD, additional_options=secret_options)

    # launch will fail if secrets are not available or not accessible
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    # tasks will fail if secret file is not created
    tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    # Verify secret content, one from each pod type
    # get tasks ids - only first pods
    hello_tasks = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks = tasks.get_task_ids(PACKAGE_NAME, "world-0")

    # make sure it has the default value
    assert secret_content_default == task_exec(world_tasks[0], "bash -c 'echo $WORLD_SECRET1_ENV'")
    assert secret_content_default == task_exec(world_tasks[0], "cat WORLD_SECRET2_FILE")
    assert secret_content_default == task_exec(world_tasks[0], "cat {}/secret3".format(PACKAGE_NAME))

    # hello tasks has container image
    assert secret_content_default == task_exec(hello_tasks[0], "bash -c 'echo $HELLO_SECRET1_ENV'")
    assert secret_content_default == task_exec(hello_tasks[0], "cat HELLO_SECRET1_FILE")
    assert secret_content_default == task_exec(hello_tasks[0], "cat HELLO_SECRET2_FILE")

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
    tasks.check_running(PACKAGE_NAME, NUM_HELLO + NUM_WORLD)

    # Verify secret content is changed

    # get task ids - only first pod
    hello_tasks = tasks.get_task_ids(PACKAGE_NAME, "hello-0")
    world_tasks = tasks.get_task_ids(PACKAGE_NAME, "world-0")

    assert secret_content_alternative == task_exec(world_tasks[0], "bash -c 'echo $WORLD_SECRET1_ENV'")
    assert secret_content_alternative == task_exec(world_tasks[0], "cat WORLD_SECRET2_FILE")
    assert secret_content_alternative == task_exec(world_tasks[0], "cat secret3")

    assert secret_content_alternative == task_exec(hello_tasks[0], "bash -c 'echo $HELLO_SECRET1_ENV'")
    assert secret_content_alternative == task_exec(hello_tasks[0], "cat HELLO_SECRET1_FILE")
    assert secret_content_alternative == task_exec(hello_tasks[0], "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets
    delete_secrets()


@pytest.mark.sanity
@pytest.mark.secrets
@pytest.mark.skip(reason="DCOS_SPACE authorization is not working in testing/master. Enable this test later.")
def test_secrets_dcos_space():
    # 1) create secrets in hello-world/somePath, i.e. hello-world/somePath/secret1 ...
    # 2) Tasks with DCOS_SPACE hello-world/somePath
    #       or some DCOS_SPACE path under hello-world/somePath
    #               (for example hello-world/somePath/anotherPath/)
    #    can access these Secrets

    install.uninstall(PACKAGE_NAME)

    # cannot access these secrets because of DCOS_SPACE authorization
    create_secrets("{}/somePath/".format(PACKAGE_NAME))

    try:
        install.install(PACKAGE_NAME, NUM_HELLO + NUM_WORLD, additional_options=options_dcos_space_test)

        plan.wait_for_completed_deployment(PACKAGE_NAME)

        assert False, "Should have failed to install"

    except AssertionError as arg:
        raise arg

    except:
        pass  # expected to fail

    # clean up and delete secrets
    delete_secrets("{}/somePath/".format(PACKAGE_NAME))


def create_secrets(path_prefix="", secret_content_arg=secret_content_default):
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


def task_exec(task_name, command):
    lines = cmd.run_cli("task exec {} {}".format(task_name, command)).split('\n')
    print(lines)
    for i in lines:
        # ignore text starting with:
        #    Overwriting Environment Variable ....
        #    Overwriting PATH ......
        if not i.isspace() and not i.startswith("Overwriting"):
            return i
    return ""
