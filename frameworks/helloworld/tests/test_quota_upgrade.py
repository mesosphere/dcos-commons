import logging

import pytest
import sdk_cmd
import sdk_install
import sdk_plan
import sdk_marathon
import sdk_utils
import sdk_upgrade
from tests import config

log = logging.getLogger(__name__)

ENFORCED_ROLE = "quota"
SERVICE_NAME = "/{}/hello-world".format(ENFORCED_ROLE)
LEGACY_ROLE = "{}-role".format(SERVICE_NAME.strip("/").replace("/", "__"))

RECOVERY_TIMEOUT_SECONDS = 20 * 60
UPGRADE_FROM = "3.1.0-0.56.0"


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, SERVICE_NAME)
        sdk_marathon.delete_group(group_id=ENFORCED_ROLE)
        # create group with enforced-roles
        sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": False})
        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, SERVICE_NAME)
        sdk_marathon.delete_group(group_id=ENFORCED_ROLE)


@pytest.mark.quota_upgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_initial_upgrade():

    options = {"service": {"name": SERVICE_NAME}}
    sdk_upgrade.test_upgrade(
        config.PACKAGE_NAME, SERVICE_NAME, 3, from_version=UPGRADE_FROM, from_options=options
    )

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE in current_task_roles.values()
    assert ENFORCED_ROLE not in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == LEGACY_ROLE


@pytest.mark.quota_upgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_update_scheduler_role():

    options = {
        "service": {
            "name": SERVICE_NAME,
            "role": ENFORCED_ROLE,
            "quota_migration_mode": True,
        }
    }
    sdk_upgrade.update_or_upgrade_or_downgrade(
        config.PACKAGE_NAME,
        SERVICE_NAME,
        expected_running_tasks=3,
        to_options=options,
        to_version=None,
    )

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE in current_task_roles.values()
    # Pods haven't been replaced yet.
    assert ENFORCED_ROLE not in current_task_roles.values()

    # Ensure we are MULTI_ROLE.
    assert service_roles["framework-roles"] is not None
    assert service_roles["framework-role"] is None

    assert len(service_roles["framework-roles"]) == 2
    assert LEGACY_ROLE in service_roles["framework-roles"]
    assert ENFORCED_ROLE in service_roles["framework-roles"]


@pytest.mark.quota_upgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_replace_pods_to_new_role():

    # Issue pod replace operations till we move the pods to the new role.
    replace_pods = ["hello-0", "world-0", "world-1"]

    for pod in replace_pods:
        # start replace and wait for it to finish
        sdk_cmd.svc_cli(config.PACKAGE_NAME, SERVICE_NAME, "pod replace {}".format(pod))
        sdk_plan.wait_for_kicked_off_recovery(
            SERVICE_NAME, timeout_seconds=RECOVERY_TIMEOUT_SECONDS
        )
        sdk_plan.wait_for_completed_recovery(SERVICE_NAME, timeout_seconds=RECOVERY_TIMEOUT_SECONDS)

        # Get the current service state to verify roles have applied.
        service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
        current_task_roles = service_roles["task-roles"]
        task_name = "{}-server".format(pod)

        # Ensure we have transitioned over to the new role.
        assert current_task_roles[task_name] == ENFORCED_ROLE

    # Get refreshed roles after pod replace's
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE not in current_task_roles.values()
    assert ENFORCED_ROLE in current_task_roles.values()

    # Ensure we're MULTI_ROLE
    assert service_roles["framework-roles"] is not None
    assert service_roles["framework-role"] is None

    assert len(service_roles["framework-roles"]) == 2
    assert LEGACY_ROLE in service_roles["framework-roles"]
    assert ENFORCED_ROLE in service_roles["framework-roles"]


@pytest.mark.quota_upgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_add_pods_post_update():

    # Add new pods to service which should be launched with the new role.
    marathon_config = sdk_marathon.get_config(SERVICE_NAME)

    # Add an extra pod to each.
    marathon_config["env"]["HELLO_COUNT"] = "2"
    marathon_config["env"]["WORLD_COUNT"] = "3"

    # Update the app
    sdk_marathon.update_app(marathon_config)

    # Wait for scheduler to restart.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0
    assert len(current_task_roles) == 5

    assert LEGACY_ROLE not in current_task_roles.values()
    assert ENFORCED_ROLE in current_task_roles.values()

    # Ensure we're MULTI_ROLE
    assert service_roles["framework-roles"] is not None
    assert service_roles["framework-role"] is None

    assert len(service_roles["framework-roles"]) == 2
    assert LEGACY_ROLE in service_roles["framework-roles"]
    assert ENFORCED_ROLE in service_roles["framework-roles"]


@pytest.mark.quota_upgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_disable_legacy_role_post_update():

    # Add new pods to service which should be launched with the new role.
    marathon_config = sdk_marathon.get_config(SERVICE_NAME)

    # Turn off legacy role.
    marathon_config["env"]["QUOTA_MIGRATION_MODE"] = "false"

    # Update the app
    sdk_marathon.update_app(marathon_config)

    # Wait for scheduler to restart.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0
    assert len(current_task_roles) == 5

    assert LEGACY_ROLE not in current_task_roles.values()
    assert ENFORCED_ROLE in current_task_roles.values()

    # Ensure we're not MULTI_ROLE, and only using the enforced-role.
    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == ENFORCED_ROLE


@pytest.mark.quota_upgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_more_pods_disable_legacy_role_post_update():
    # Ensure we can scale out more still with legacy role disabled.

    # Add new pods to service which should be launched with the new role.
    marathon_config = sdk_marathon.get_config(SERVICE_NAME)

    # Add an extra pod to each.
    marathon_config["env"]["HELLO_COUNT"] = "3"
    marathon_config["env"]["WORLD_COUNT"] = "4"

    # Update the app
    sdk_marathon.update_app(marathon_config)

    # Wait for scheduler to restart.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0
    assert len(current_task_roles) == 7

    assert LEGACY_ROLE not in current_task_roles.values()
    assert ENFORCED_ROLE in current_task_roles.values()

    # Ensure we're MULTI_ROLE
    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == ENFORCED_ROLE
