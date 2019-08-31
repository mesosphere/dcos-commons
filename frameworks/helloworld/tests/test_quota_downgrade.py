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
DOWNGRADE_TO = "3.1.0-0.56.0"


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, SERVICE_NAME)
        sdk_marathon.delete_group(group_id=ENFORCED_ROLE)
        # create group with enforced-roles
        sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": True})
        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, SERVICE_NAME)
        sdk_marathon.delete_group(group_id=ENFORCED_ROLE)


@pytest.mark.quota_downgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_initial_install():

    # Create group without enforced roles.
    options = {"service": {"name": SERVICE_NAME}}

    # this config produces 1 hello's + 2 world's:
    sdk_install.install(config.PACKAGE_NAME, SERVICE_NAME, 3, additional_options=options)

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE not in current_task_roles.values()
    assert ENFORCED_ROLE in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == ENFORCED_ROLE


@pytest.mark.quota_downgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_disable_enforce_role():

    # Turn off enforce role
    sdk_cmd.run_cli("marathon group update /{} enforceRole=false".format(ENFORCED_ROLE))

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE not in current_task_roles.values()
    assert ENFORCED_ROLE in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == ENFORCED_ROLE


@pytest.mark.quota_downgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_switch_to_legacy_role():

    options = {
        "service": {
            "name": SERVICE_NAME,
            "role": "slave_public",
            "enable_role_migration": True,
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

    assert LEGACY_ROLE not in current_task_roles.values()
    assert ENFORCED_ROLE in current_task_roles.values()

    assert service_roles["framework-roles"] is not None
    assert service_roles["framework-role"] is None

    assert len(service_roles["framework-roles"]) == 2
    assert LEGACY_ROLE in service_roles["framework-roles"]
    assert ENFORCED_ROLE in service_roles["framework-roles"]


@pytest.mark.quota_downgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_replace_pods_to_legacy_role():

    # Issue pod replace operations till we move the pods to the legacy role.
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

        # Ensure we have transitioned over to the legacy role.
        assert current_task_roles[task_name] == LEGACY_ROLE

    # Get refreshed roles after pod replace's
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE in current_task_roles.values()
    assert ENFORCED_ROLE not in current_task_roles.values()

    # Ensure we're MULTI_ROLE
    assert service_roles["framework-roles"] is not None
    assert service_roles["framework-role"] is None

    assert len(service_roles["framework-roles"]) == 2
    assert LEGACY_ROLE in service_roles["framework-roles"]
    assert ENFORCED_ROLE in service_roles["framework-roles"]


@pytest.mark.quota_downgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_disable_quota_role():

    # Add new pods to service which should be launched with the new role.
    marathon_config = sdk_marathon.get_config(SERVICE_NAME)

    # Turn off legacy role.
    marathon_config["env"]["ENABLE_ROLE_MIGRATION"] = "false"

    # Update the app
    sdk_marathon.update_app(marathon_config)

    # Wait for scheduler to restart.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0
    assert len(current_task_roles) == 3

    assert LEGACY_ROLE in current_task_roles.values()
    assert ENFORCED_ROLE not in current_task_roles.values()

    # Ensure we're not MULTI_ROLE, and only using the legacy-role.
    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == LEGACY_ROLE


@pytest.mark.quota_downgrade
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

    assert LEGACY_ROLE in current_task_roles.values()
    assert ENFORCED_ROLE not in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == LEGACY_ROLE


@pytest.mark.quota_downgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_downgrade_scheduler():

    options = {"service": {"name": SERVICE_NAME}, "hello": {"count": 2}, "world": {"count": 3}}
    sdk_upgrade.test_downgrade(
        config.PACKAGE_NAME, SERVICE_NAME, 5, to_version=DOWNGRADE_TO, to_options=options
    )

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0
    assert len(current_task_roles) == 5

    assert LEGACY_ROLE in current_task_roles.values()
    assert ENFORCED_ROLE not in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == LEGACY_ROLE
