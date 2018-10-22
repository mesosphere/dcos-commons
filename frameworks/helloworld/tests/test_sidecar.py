import logging

import pytest
import retrying

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_upgrade
import sdk_utils

from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {"service": {"yaml": "sidecar"}}

        # this yml has 2 hello's + 0 world's:
        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 2, additional_options=options)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_envvar_accross_restarts():
    sleep_duration = 9999
    sdk_upgrade.update_or_upgrade_or_downgrade(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        to_package_version=None,
        additional_options={
            "service": {"name": config.SERVICE_NAME, "sleep": sleep_duration, "yaml": "sidecar"}
        },
        expected_running_tasks=2,
        wait_for_deployment=True,
    )

    for attempt in range(3):
        cmd_list = ["pod", "restart", "hello-0"]
        sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, " ".join(cmd_list))

        sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)
        sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)

        _, stdout, _ = sdk_cmd.service_task_exec(config.SERVICE_NAME, "hello-0-server", "env")

        envvar = "CONFIG_SLEEP_DURATION="
        envvar_pos = stdout.find(envvar)
        if envvar_pos < 0:
            raise Exception("Required envvar not found")

        if not stdout[envvar_pos + len(envvar) :].startswith("{}".format(sleep_duration)):
            found_string = stdout[envvar_pos + len(envvar) : envvar_pos + len(envvar) + 15]
            log.error(
                "(%d) Looking for %s%d but found: %s", attempt, envvar, sleep_duration, found_string
            )
            raise Exception("Envvar not set to required value")


@pytest.mark.sanity
def test_deploy():
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    deployment_plan = sdk_plan.get_deployment_plan(config.SERVICE_NAME)
    log.info(sdk_plan.plan_string("deploy", deployment_plan))

    assert len(deployment_plan["phases"]) == 2
    assert deployment_plan["phases"][0]["name"] == "server-deploy"
    assert deployment_plan["phases"][1]["name"] == "once-deploy"
    assert len(deployment_plan["phases"][0]["steps"]) == 2
    assert len(deployment_plan["phases"][1]["steps"]) == 2


@pytest.mark.sanity
def test_sidecar():
    run_plan("sidecar")


@pytest.mark.sanity
def test_sidecar_parameterized():
    run_plan("sidecar-parameterized", {"PLAN_PARAMETER": "parameterized"})


@retrying.retry(wait_fixed=2000, stop_max_delay=5 * 60 * 1000, retry_on_result=lambda res: not res)
def wait_for_toxic_sidecar():
    """
    Since the sidecar task fails too quickly, we check for the contents of
    the file generated in hello-container-path/toxic-output instead

    Note that we only check the output of hello-0.

    In DC/OS prior to version 1.10, task exec does not run the command in the MESOS_SANDBOX directory and this
    causes the check of the file contents to fail. Here we simply rely on the existence of the file.
    """
    if sdk_utils.dcos_version_less_than("1.10"):
        # Note: As of this writing, 'task ls' does 'contains' comparisons of task ids correctly,
        # so we don't need to include a service name prefix here.
        _, output, _ = sdk_cmd.run_cli("task ls hello-0-server hello-container-path/toxic-output")
        expected_output = ""
    else:
        _, output, _ = sdk_cmd.service_task_exec(
            config.SERVICE_NAME, "hello-0-server", "cat hello-container-path/toxic-output"
        )
        expected_output = "I'm addicted to you / Don't you know that you're toxic?"
    return output.strip() == expected_output


@pytest.mark.sanity
def test_toxic_sidecar_doesnt_trigger_recovery():
    # 1. Run the toxic sidecar plan that will never succeed.
    # 2. Restart the scheduler.
    # 3. Verify that its recovery plan has not changed, as a failed ONCE task should
    # never trigger recovery
    initial_recovery_plan = sdk_plan.get_plan(config.SERVICE_NAME, "recovery")
    assert initial_recovery_plan["status"] == "COMPLETE"
    log.info(initial_recovery_plan)
    sdk_plan.start_plan(config.SERVICE_NAME, "sidecar-toxic")
    wait_for_toxic_sidecar()

    # Restart the scheduler and wait for it to come up.
    sdk_marathon.restart_app(config.SERVICE_NAME)
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # Now, verify that its recovery plan hasn't changed.
    final_recovery_plan = sdk_plan.get_plan(config.SERVICE_NAME, "recovery")
    assert initial_recovery_plan["status"] == final_recovery_plan["status"]


def run_plan(plan_name, params=None):
    sdk_plan.start_plan(config.SERVICE_NAME, plan_name, params)

    started_plan = sdk_plan.get_plan(config.SERVICE_NAME, plan_name)
    log.info(sdk_plan.plan_string(plan_name, started_plan))
    assert len(started_plan["phases"]) == 1
    assert started_plan["phases"][0]["name"] == plan_name + "-deploy"
    assert len(started_plan["phases"][0]["steps"]) == 2

    sdk_plan.wait_for_completed_plan(config.SERVICE_NAME, plan_name)
