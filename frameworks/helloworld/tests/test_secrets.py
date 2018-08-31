import logging
import retrying

import pytest
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils
from tests import config

log = logging.getLogger(__name__)

NUM_HELLO = 2
NUM_WORLD = 3

secret_content_default = "hello-world-secret-data"
secret_content_alternative = secret_content_default + "-alternative"

secret_options = {
    "service": {"yaml": "secrets"},
    "hello": {
        "count": NUM_HELLO,
        "secret1": "hello-world/secret1",
        "secret2": "hello-world/secret2",
    },
    "world": {
        "count": NUM_WORLD,
        "secret1": "hello-world/secret1",
        "secret2": "hello-world/secret2",
        "secret3": "hello-world/secret3",
    },
}

options_dcos_space_test = {
    "service": {"yaml": "secrets"},
    "hello": {
        "count": NUM_HELLO,
        "secret1": "hello-world/somePath/secret1",
        "secret2": "hello-world/somePath/secret2",
    },
    "world": {
        "count": NUM_WORLD,
        "secret1": "hello-world/somePath/secret1",
        "secret2": "hello-world/somePath/secret2",
        "secret3": "hello-world/somePath/secret3",
    },
}


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_cmd.run_cli("package install --cli dcos-enterprise-cli --yes")
        delete_secrets("{}/".format(config.SERVICE_NAME))
        delete_secrets("{}/somePath/".format(config.SERVICE_NAME))
        delete_secrets()

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        delete_secrets("{}/".format(config.SERVICE_NAME))
        delete_secrets("{}/somePath/".format(config.SERVICE_NAME))
        delete_secrets()


@pytest.mark.sanity
@pytest.mark.smoke
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version("1.10")
def test_secrets_basic():
    # 1) create Secrets
    # 2) install examples/secrets.yml
    # 3) if secret file is not created, tasks will fail
    # 4) wait till deployment finishes
    # 5) do replace operation
    # 6) ensure all tasks are running
    # 7) delete Secrets

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    create_secrets("{}/".format(config.SERVICE_NAME))

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        NUM_HELLO + NUM_WORLD,
        additional_options=secret_options,
    )

    hello_tasks_0 = sdk_tasks.get_task_ids(config.SERVICE_NAME, "hello-0-server")
    world_tasks_0 = sdk_tasks.get_task_ids(config.SERVICE_NAME, "word-0-server")

    # ensure that secrets work after replace
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "pod replace hello-0")
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "pod replace world-0")

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0-server", hello_tasks_0)
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "world-0-server", world_tasks_0)

    # tasks will fail if secret files are not created by mesos module
    sdk_tasks.check_running(config.SERVICE_NAME, NUM_HELLO + NUM_WORLD)

    # clean up and delete secrets
    delete_secrets("{}/".format(config.SERVICE_NAME))


@pytest.mark.sanity
@pytest.mark.smoke
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version("1.10")
def test_secrets_verify():
    # 1) create Secrets
    # 2) install examples/secrets.yml
    # 3) verify Secrets content
    # 4) delete Secrets

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    create_secrets("{}/".format(config.SERVICE_NAME))

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        NUM_HELLO + NUM_WORLD,
        additional_options=secret_options,
    )

    # tasks will fail if secret file is not created
    sdk_tasks.check_running(config.SERVICE_NAME, NUM_HELLO + NUM_WORLD)

    # Verify secret content, one from each pod type

    # first secret: environment variable name is given in yaml
    assert secret_content_default == read_secret(
        "world-0-server", "bash -c 'echo $WORLD_SECRET1_ENV'"
    )

    # second secret: file path is given in yaml
    assert secret_content_default == read_secret("world-0-server", "cat WORLD_SECRET2_FILE")

    # third secret : no file path is given in yaml
    #            default file path is equal to secret path
    assert secret_content_default == read_secret("world-0-server", "cat hello-world/secret3")

    # hello tasks has container image, world tasks do not

    # first secret : environment variable name is given in yaml
    assert secret_content_default == read_secret(
        "hello-0-server", "bash -c 'echo $HELLO_SECRET1_ENV'"
    )

    # first secret : both environment variable name and file path are given in yaml
    assert secret_content_default == read_secret("hello-0-server", "cat HELLO_SECRET1_FILE")

    # second secret : file path is given in yaml
    assert secret_content_default == read_secret("hello-0-server", "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets
    delete_secrets("{}/".format(config.SERVICE_NAME))


@pytest.mark.sanity
@pytest.mark.smoke
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version("1.10")
def test_secrets_update():
    # 1) create Secrets
    # 2) install examples/secrets.yml
    # 3) update Secrets
    # 4) restart task
    # 5) verify Secrets content (updated after restart)
    # 6) delete Secrets

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    create_secrets("{}/".format(config.SERVICE_NAME))

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        NUM_HELLO + NUM_WORLD,
        additional_options=secret_options,
    )

    # tasks will fail if secret file is not created
    sdk_tasks.check_running(config.SERVICE_NAME, NUM_HELLO + NUM_WORLD)

    def update_secret(secret_name):
        sdk_cmd.run_cli(
            "security secrets update --value={} {}".format(secret_content_alternative, secret_name)
        )

    update_secret("{}/secret1".format(config.SERVICE_NAME))
    update_secret("{}/secret2".format(config.SERVICE_NAME))
    update_secret("{}/secret3".format(config.SERVICE_NAME))

    # Verify with hello-0 and world-0, just check with one of the pods

    hello_tasks_old = sdk_tasks.get_task_ids(config.SERVICE_NAME, "hello-0-server")
    world_tasks_old = sdk_tasks.get_task_ids(config.SERVICE_NAME, "world-0-server")

    # restart pods to retrieve new secret's content
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "pod restart hello-0")
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "pod restart world-0")

    # wait pod restart to complete
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello-0-server", hello_tasks_old)
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "world-0-server", world_tasks_old)

    # wait till it is running
    sdk_tasks.check_running(config.SERVICE_NAME, NUM_HELLO + NUM_WORLD)

    # make sure content is changed
    assert secret_content_alternative == read_secret(
        "world-0-server", "bash -c 'echo $WORLD_SECRET1_ENV'"
    )
    assert secret_content_alternative == read_secret("world-0-server", "cat WORLD_SECRET2_FILE")
    assert secret_content_alternative == read_secret(
        "world-0-server", "cat {}/secret3".format(config.SERVICE_NAME)
    )

    # make sure content is changed
    assert secret_content_alternative == read_secret(
        "hello-0-server", "bash -c 'echo $HELLO_SECRET1_ENV'"
    )
    assert secret_content_alternative == read_secret("hello-0-server", "cat HELLO_SECRET1_FILE")
    assert secret_content_alternative == read_secret("hello-0-server", "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets
    delete_secrets("{}/".format(config.SERVICE_NAME))


@pytest.mark.sanity
@pytest.mark.smoke
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version("1.10")
def test_secrets_config_update():
    # 1) install examples/secrets.yml
    # 2) create new Secrets, delete old Secrets
    # 2) update configuration with new Secrets
    # 4) verify secret content (using new Secrets after config update)

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    create_secrets("{}/".format(config.SERVICE_NAME))

    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        NUM_HELLO + NUM_WORLD,
        additional_options=secret_options,
    )

    # tasks will fail if secret file is not created
    sdk_tasks.check_running(config.SERVICE_NAME, NUM_HELLO + NUM_WORLD)

    # Verify secret content, one from each pod type

    # make sure it has the default value
    assert secret_content_default == read_secret(
        "world-0-server", "bash -c 'echo $WORLD_SECRET1_ENV'"
    )
    assert secret_content_default == read_secret("world-0-server", "cat WORLD_SECRET2_FILE")
    assert secret_content_default == read_secret(
        "world-0-server", "cat {}/secret3".format(config.SERVICE_NAME)
    )

    # hello tasks has container image
    assert secret_content_default == read_secret(
        "hello-0-server", "bash -c 'echo $HELLO_SECRET1_ENV'"
    )
    assert secret_content_default == read_secret("hello-0-server", "cat HELLO_SECRET1_FILE")
    assert secret_content_default == read_secret("hello-0-server", "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets (defaults)
    delete_secrets("{}/".format(config.SERVICE_NAME))

    # create new secrets with new content -- New Value
    create_secrets(secret_content=secret_content_alternative)

    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config["env"]["HELLO_SECRET1"] = "secret1"
    marathon_config["env"]["HELLO_SECRET2"] = "secret2"
    marathon_config["env"]["WORLD_SECRET1"] = "secret1"
    marathon_config["env"]["WORLD_SECRET2"] = "secret2"
    marathon_config["env"]["WORLD_SECRET3"] = "secret3"

    # config update
    sdk_marathon.update_app(marathon_config)

    # wait till plan is complete - pods are supposed to restart
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # all tasks are running
    sdk_tasks.check_running(config.SERVICE_NAME, NUM_HELLO + NUM_WORLD)

    # Verify secret content is changed

    assert secret_content_alternative == read_secret(
        "world-0-server", "bash -c 'echo $WORLD_SECRET1_ENV'"
    )
    assert secret_content_alternative == read_secret("world-0-server", "cat WORLD_SECRET2_FILE")
    assert secret_content_alternative == read_secret("world-0-server", "cat secret3")

    assert secret_content_alternative == read_secret(
        "hello-0-server", "bash -c 'echo $HELLO_SECRET1_ENV'"
    )
    assert secret_content_alternative == read_secret("hello-0-server", "cat HELLO_SECRET1_FILE")
    assert secret_content_alternative == read_secret("hello-0-server", "cat HELLO_SECRET2_FILE")

    # clean up and delete secrets
    delete_secrets()


@pytest.mark.sanity
@pytest.mark.smoke
@sdk_utils.dcos_ee_only
@pytest.mark.dcos_min_version("1.10")
def test_secrets_dcos_space():
    # 1) create secrets in hello-world/somePath, i.e. hello-world/somePath/secret1 ...
    # 2) Tasks with DCOS_SPACE hello-world/somePath
    #       or some DCOS_SPACE path under hello-world/somePath
    #               (for example hello-world/somePath/anotherPath/)
    #    can access these Secrets

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

    # cannot access these secrets because of DCOS_SPACE authorization
    create_secrets("{}/somePath/".format(config.SERVICE_NAME))

    # Disable any wait operations within the install call.
    # - Don't wait for tasks to deploy (they won't)
    # - Don't wait for deploy plan to complete (it won't)
    # Instead, we manually verify that the service is stuck below.
    sdk_install.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        0,
        additional_options=options_dcos_space_test,
        wait_for_deployment=False,
    )

    try:
        # Now, manually check that the deploy plan is stuck. Just wait for 5 minutes.
        sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME, timeout_seconds=5 * 60)
        assert False, "Should have failed to install"
    except AssertionError as e:
        raise e
    except Exception:
        log.info("Deployment failed as expected")
        pass  # Plan is expected to not complete

    # clean up and delete secrets
    delete_secrets("{}/somePath/".format(config.SERVICE_NAME))


def create_secrets(path_prefix="", secret_content=secret_content_default):
    def create_secret(secret_name):
        sdk_cmd.run_cli(
            "security secrets create --value={} {}".format(secret_content, secret_name)
        )

    create_secret("{}secret1".format(path_prefix))
    create_secret("{}secret2".format(path_prefix))
    create_secret("{}secret3".format(path_prefix))


def delete_secrets(path_prefix=""):
    def delete_secret(secret_name):
        sdk_cmd.run_cli("security secrets delete {}".format(secret_name))

    delete_secret("{}secret1".format(path_prefix))
    delete_secret("{}secret2".format(path_prefix))
    delete_secret("{}secret3".format(path_prefix))


@retrying.retry(wait_fixed=2000, stop_max_delay=5 * 60 * 1000)
def read_secret(task_name, command):
    _, output, _ = sdk_cmd.service_task_exec(config.SERVICE_NAME, task_name, command)
    lines = [line.strip() for line in output.split("\n")]
    log.info("Looking for %s...", secret_content_default)
    for line in lines:
        if line.startswith(secret_content_default):
            return line
    raise Exception("Failed to read secret from {} with command '{}'".format(task_name, command))
