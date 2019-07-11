import logging

import pytest
import sdk_cmd
import sdk_install
import sdk_plan
import sdk_marathon
from tests import config

log = logging.getLogger(__name__)
MARATHON_TASK_ROLE = "test-role"
MARATHON_TASK_ROLE_ENV = "DCOS_NAMESPACE"

RECOVERY_TIMEOUT_SECONDS = 20 * 60


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        # this config produces 1 hello's + 2 world's:
        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 3)
        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_initial_deploy():
    # Ensure that our default service installs to a complete deployment.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)
    deployment_plan = sdk_plan.get_deployment_plan(config.SERVICE_NAME)
    log.info(sdk_plan.plan_string("deploy", deployment_plan))


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_apply_new_scheduler_role():
    # Apply the new role and ensure that the previous deployment and pods
    # haven't been affected.

    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config["env"][MARATHON_TASK_ROLE_ENV] = MARATHON_TASK_ROLE

    # Update the app
    sdk_marathon.update_app(marathon_config)

    # Wait for scheduler to restart.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # Assert the deployment plans are complete.
    deployment_plan = sdk_plan.get_deployment_plan(config.SERVICE_NAME)
    assert deployment_plan["status"] == "COMPLETE"
    log.info(sdk_plan.plan_string("deploy", deployment_plan))

    # Get the current service state to verify roles have applied.
    current_task_roles = _get_service_task_roles()

    # We must have some role!
    assert len(current_task_roles) > 0
    # Ensure that role change hasn't started yet.
    assert MARATHON_TASK_ROLE not in current_task_roles.values()


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_replace_pods_to_new_role():
    # Incrementally deploy pods with new role.
    # Ensure we're fully deployed.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # Issue pod replace operations till we move the pods to the new role.
    replace_pods = ["hello-0", "world-0", "world-1"]

    for pod in replace_pods:
        # start replace and wait for it to finish
        sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, "pod replace {}".format(pod))
        sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)
        sdk_plan.wait_for_completed_recovery(
            config.SERVICE_NAME, timeout_seconds=RECOVERY_TIMEOUT_SECONDS
        )

        # Get the current service state to verify roles have applied.
        current_task_roles = _get_service_task_roles()
        task_name = "{}-server".format(pod)

        # Ensure we have transitioned over to the new role.
        assert current_task_roles[task_name] == MARATHON_TASK_ROLE

        # Force restart of the app to ensure that the scheduler can be restarted
        # at any point during the migration.
        sdk_marathon.restart_app(config.SERVICE_NAME)

    # Ensure on the last restart that the scheduler is backup
    # before continuing with the tests.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_add_pods_with_new_role():

    # Add new pods to service which should be launched with the new role.
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)

    # Add an extra pod to each.
    marathon_config["env"]["HELLO_COUNT"] = "2"
    marathon_config["env"]["WORLD_COUNT"] = "3"

    # Update the app
    sdk_marathon.update_app(marathon_config)

    # Wait for scheduler to restart.
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # Get the current service state to verify roles have applied.
    current_task_roles = _get_service_task_roles()

    # Ensure we have all tasks.
    assert len(current_task_roles) == 5

    roles_set = set(current_task_roles.values())

    # Ensure we only have one role.
    assert len(roles_set) == 1

    # Ensure that role is what we expect.
    assert MARATHON_TASK_ROLE in roles_set


def _get_service_task_roles() -> dict:
    # Get the current service state to verify roles have applied.
    mesos_state = sdk_cmd.cluster_request("GET", "/mesos/master/state").json()

    service_state = None
    current_task_roles = {}

    # Find our service.
    for service in mesos_state["frameworks"]:
        if service["name"] == config.SERVICE_NAME:
            service_state = service

    # Create a map of tasks to roles.
    if service_state:
        current_tasks = service_state["tasks"]
        for task in current_tasks:
            current_task_roles[task["name"]] = task["role"]

    return current_task_roles
