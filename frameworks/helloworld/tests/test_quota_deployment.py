import logging

import pytest
import sdk_install
import sdk_plan
import sdk_marathon
import sdk_utils
from tests import config

log = logging.getLogger(__name__)

ENFORCED_ROLE = "test"
SERVICE_NAME = "/{}/integration/hello-world".format(ENFORCED_ROLE)
LEGACY_ROLE = "{}-role".format(SERVICE_NAME.strip("/").replace("/", "__"))

RECOVERY_TIMEOUT_SECONDS = 20 * 60

# This test does a fresh install of a new service and verifies that role
# creation was correctly done.
@pytest.fixture(autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, SERVICE_NAME)
        sdk_marathon.delete_group(group_id=ENFORCED_ROLE)


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_nonenforced_group_role_defaults():

    # Create group without enforced roles.
    sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": False})
    options = {"service": {"name": SERVICE_NAME}}

    # this config produces 1 hello's + 2 world's:
    sdk_install.install(config.PACKAGE_NAME, SERVICE_NAME, 3, additional_options=options)

    # Ensure that our default service installs to a complete deployment.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Assert the deployment plans are complete.
    deployment_plan = sdk_plan.get_deployment_plan(SERVICE_NAME)
    assert deployment_plan["status"] == "COMPLETE"

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE in current_task_roles.values()
    assert ENFORCED_ROLE not in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == LEGACY_ROLE


# @pytest.mark.quota
# @pytest.mark.dcos_min_version("1.14")
# @pytest.mark.sanity
# def test_nonenforced_group_role_service_role_set():

#     # Create group without enforced roles.
#     sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": False})
#     options = {"service": {"name": SERVICE_NAME, "service_role": ENFORCED_ROLE}}

#     # this config produces 1 hello's + 2 world's:
#     sdk_install.install(config.PACKAGE_NAME, SERVICE_NAME, 3, additional_options=options)

#     # Ensure that our default service installs to a complete deployment.
#     sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

#     # Assert the deployment plans are complete.
#     deployment_plan = sdk_plan.get_deployment_plan(SERVICE_NAME)
#     assert deployment_plan["status"] == "COMPLETE"

#     # Get the current service state to verify roles have applied.
#     service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
#     current_task_roles = service_roles["task-roles"]

#     # We must have some role!
#     assert len(current_task_roles) > 0

#     assert LEGACY_ROLE not in current_task_roles.values()
#     assert ENFORCED_ROLE in current_task_roles.values()

#     assert service_roles["framework-roles"] is None
#     assert service_roles["framework-role"] == ENFORCED_ROLE


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_nonenforced_group_legacy_service_role():

    # Create group without enforced roles.
    sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": False})
    options = {"service": {"name": SERVICE_NAME, "service_role": "slave_public"}}

    # this config produces 1 hello's + 2 world's:
    sdk_install.install(config.PACKAGE_NAME, SERVICE_NAME, 3, additional_options=options)

    # Ensure that our default service installs to a complete deployment.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Assert the deployment plans are complete.
    deployment_plan = sdk_plan.get_deployment_plan(SERVICE_NAME)
    assert deployment_plan["status"] == "COMPLETE"

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE in current_task_roles.values()
    assert ENFORCED_ROLE not in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == LEGACY_ROLE


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_nonenforced_group_role_service_role_legacy_role_set():

    # Create group without enforced roles.
    sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": False})
    options = {
        "service": {
            "name": SERVICE_NAME,
            "service_role": ENFORCED_ROLE,
            "quota_migration_mode": True,
        }
    }

    # this config produces 1 hello's + 2 world's:
    sdk_install.install(config.PACKAGE_NAME, SERVICE_NAME, 3, additional_options=options)

    # Ensure that our default service installs to a complete deployment.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Assert the deployment plans are complete.
    deployment_plan = sdk_plan.get_deployment_plan(SERVICE_NAME)
    assert deployment_plan["status"] == "COMPLETE"

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


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_enforced_group_role_defaults():

    # Create group without enforced roles.
    sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": True})
    options = {"service": {"name": SERVICE_NAME}}

    # this config produces 1 hello's + 2 world's:
    sdk_install.install(config.PACKAGE_NAME, SERVICE_NAME, 3, additional_options=options)

    # Ensure that our default service installs to a complete deployment.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Assert the deployment plans are complete.
    deployment_plan = sdk_plan.get_deployment_plan(SERVICE_NAME)
    assert deployment_plan["status"] == "COMPLETE"

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE not in current_task_roles.values()
    assert ENFORCED_ROLE in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == ENFORCED_ROLE


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_enforced_group_role_legacy_role_set():

    # Create group without enforced roles.
    sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": True})
    options = {"service": {"name": SERVICE_NAME, "quota_migration_mode": True}}

    # this config produces 1 hello's + 2 world's:
    sdk_install.install(config.PACKAGE_NAME, SERVICE_NAME, 3, additional_options=options)

    # Ensure that our default service installs to a complete deployment.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Assert the deployment plans are complete.
    deployment_plan = sdk_plan.get_deployment_plan(SERVICE_NAME)
    assert deployment_plan["status"] == "COMPLETE"

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


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_nonenforced_group_legacy_service_role_non_migration():

    # Create group without enforced roles.
    sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": False})
    options = {
        "service": {
            "name": SERVICE_NAME,
            "service_role": "slave_public",
            "quota_migration_mode": False,
        }
    }

    # this config produces 1 hello's + 2 world's:
    sdk_install.install(config.PACKAGE_NAME, SERVICE_NAME, 3, additional_options=options)

    # Ensure that our default service installs to a complete deployment.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Assert the deployment plans are complete.
    deployment_plan = sdk_plan.get_deployment_plan(SERVICE_NAME)
    assert deployment_plan["status"] == "COMPLETE"

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE in current_task_roles.values()
    assert ENFORCED_ROLE not in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == LEGACY_ROLE


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_enforced_role_non_migration():

    # Create group without enforced roles.
    sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": False})
    options = {
        "service": {
            "name": SERVICE_NAME,
            "service_role": ENFORCED_ROLE,
            "quota_migration_mode": False,
        }
    }

    # this config produces 1 hello's + 2 world's:
    sdk_install.install(config.PACKAGE_NAME, SERVICE_NAME, 3, additional_options=options)

    # Ensure that our default service installs to a complete deployment.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Assert the deployment plans are complete.
    deployment_plan = sdk_plan.get_deployment_plan(SERVICE_NAME)
    assert deployment_plan["status"] == "COMPLETE"

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE not in current_task_roles.values()
    assert ENFORCED_ROLE in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == ENFORCED_ROLE


@pytest.mark.quota
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_group_enforced_role_non_migration():

    # Create group without enforced roles.
    sdk_marathon.create_group(group_id=ENFORCED_ROLE, options={"enforceRole": True})
    options = {
        "service": {
            "name": SERVICE_NAME,
            "service_role": ENFORCED_ROLE,
            "quota_migration_mode": False,
        }
    }

    # this config produces 1 hello's + 2 world's:
    sdk_install.install(config.PACKAGE_NAME, SERVICE_NAME, 3, additional_options=options)

    # Ensure that our default service installs to a complete deployment.
    sdk_plan.wait_for_completed_deployment(SERVICE_NAME)

    # Assert the deployment plans are complete.
    deployment_plan = sdk_plan.get_deployment_plan(SERVICE_NAME)
    assert deployment_plan["status"] == "COMPLETE"

    # Get the current service state to verify roles have applied.
    service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
    current_task_roles = service_roles["task-roles"]

    # We must have some role!
    assert len(current_task_roles) > 0

    assert LEGACY_ROLE not in current_task_roles.values()
    assert ENFORCED_ROLE in current_task_roles.values()

    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == ENFORCED_ROLE
