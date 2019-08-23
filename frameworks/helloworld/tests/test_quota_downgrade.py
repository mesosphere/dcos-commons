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
MARATHON_APP_ENFORCE_GROUP_ROLE = "true"
ENFORCED_ROLE = "foo"
LEGACY_ROLE = "{}__hello-world-role".format(ENFORCED_ROLE)

RECOVERY_TIMEOUT_SECONDS = 20 * 60
SERVICE_NAME = "/{}/hello-world".format(ENFORCED_ROLE)
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
        log.info("DELETEME@kjoshi remove this later!")
        # sdk_install.uninstall(config.PACKAGE_NAME, SERVICE_NAME)
        # sdk_marathon.delete_group(group_id=ENFORCED_ROLE)


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

    # Ensure we're not MULTI_ROLE, and using the legacy role.
    assert service_roles["framework-roles"] is not None
    assert service_roles["framework-role"] is None

    assert len(service_roles["framework-roles"]) == 2
    assert LEGACY_ROLE in service_roles["framework-roles"]
    assert ENFORCED_ROLE in service_roles["framework-roles"]


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

    # Ensure we're not MULTI_ROLE, and using the legacy role.
    assert service_roles["framework-roles"] is not None
    assert service_roles["framework-role"] is None

    assert len(service_roles["framework-roles"]) == 2
    assert LEGACY_ROLE in service_roles["framework-roles"]
    assert ENFORCED_ROLE in service_roles["framework-roles"]


@pytest.mark.quota_downgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_switch_to_legacy_role():

    options = {"service": {"name": SERVICE_NAME, "service_role": "slave_public"}}
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

    # Ensure we're not MULTI_ROLE, and using the legacy role.
    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] is not None

    assert service_roles["framework-role"] == LEGACY_ROLE
