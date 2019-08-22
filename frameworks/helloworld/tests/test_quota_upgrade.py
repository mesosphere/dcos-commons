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
        log.info("DELETEME@kjoshi")
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

    # Ensure we're not MULTI_ROLE, and using the legacy role.
    assert service_roles["framework-roles"] is None
    assert service_roles["framework-role"] == LEGACY_ROLE


@pytest.mark.quota_upgrade
@pytest.mark.dcos_min_version("1.14")
@pytest.mark.sanity
def test_update_scheduler_role():

    options = {"service": {"name": SERVICE_NAME, "service_role": ENFORCED_ROLE}}
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

    # Ensure we're not MULTI_ROLE, and using the legacy role.
    assert service_roles["framework-roles"] is not None
    assert service_roles["framework-role"] is None


# @pytest.mark.quota_upgrade
# @pytest.mark.dcos_min_version("1.14")
# @pytest.mark.sanity
# def test_replace_pods_to_new_role():

#     # Issue pod replace operations till we move the pods to the new role.
#     replace_pods = ["hello-0", "world-0", "world-1"]

#     for pod in replace_pods:
#         # start replace and wait for it to finish
#         sdk_cmd.svc_cli(config.PACKAGE_NAME, SERVICE_NAME, "pod replace {}".format(pod))
#         sdk_plan.wait_for_kicked_off_recovery(SERVICE_NAME)
#         sdk_plan.wait_for_completed_recovery(SERVICE_NAME, timeout_seconds=RECOVERY_TIMEOUT_SECONDS)

#         # Get the current service state to verify roles have applied.
#         service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
#         current_task_roles = service_roles["task-roles"]
#         task_name = "{}-server".format(pod)

#         # Ensure we have transitioned over to the new role.
#         assert current_task_roles[task_name] == ENFORCED_ROLE

#     # Get refreshed roles after pod replace's
#     service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
#     current_task_roles = service_roles["task-roles"]

#     # We must have some role!
#     assert len(current_task_roles) > 0

#     assert LEGACY_ROLE not in current_task_roles.values()
#     assert ENFORCED_ROLE in current_task_roles.values()

#     # Ensure we're MULTI_ROLE
#     assert service_roles["framework-roles"] is not None
#     assert service_roles["framework-role"] is None

#     assert len(service_roles["framework-roles"]) == 2
#     assert LEGACY_ROLE in service_roles["framework-roles"]
#     assert ENFORCED_ROLE in service_roles["framework-roles"]


# @pytest.mark.quota_upgrade
# @pytest.mark.dcos_min_version("1.14")
# @pytest.mark.sanity
# def test_add_pods_post_update():

#     # Ensure we can scale out by adding one pod to
#     # both hello and world, this pod must be in the
#     # enforced role.
#     options = {
#         "service": {"name": SERVICE_NAME, "service_role": ENFORCED_ROLE},
#         "hello": {"count": 2},
#         "world": {"count": 3},
#     }
#     sdk_upgrade.update_or_upgrade_or_downgrade(
#         config.PACKAGE_NAME,
#         SERVICE_NAME,
#         expected_running_tasks=5,
#         to_options=options,
#         to_version=None,
#     )

#     # Get the current service state to verify roles have applied.
#     service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
#     current_task_roles = service_roles["task-roles"]

#     # We must have some role!
#     assert len(current_task_roles) > 0
#     assert len(current_task_roles) == 5

#     assert LEGACY_ROLE not in current_task_roles.values()
#     assert ENFORCED_ROLE in current_task_roles.values()

#     # Ensure we're MULTI_ROLE
#     assert service_roles["framework-roles"] is not None
#     assert service_roles["framework-role"] is None

#     assert len(service_roles["framework-roles"]) == 2
#     assert LEGACY_ROLE in service_roles["framework-roles"]
#     assert ENFORCED_ROLE in service_roles["framework-roles"]


# @pytest.mark.quota_upgrade
# @pytest.mark.dcos_min_version("1.14")
# @pytest.mark.sanity
# def test_disable_legacy_role_post_update():
#     options = {
#         "service": {
#             "name": SERVICE_NAME,
#             "service_role": ENFORCED_ROLE,
#             "subscribe_legacy_role": False,
#         },
#         "hello": {"count": 2},
#         "world": {"count": 3},
#     }
#     sdk_upgrade.update_or_upgrade_or_downgrade(
#         config.PACKAGE_NAME,
#         SERVICE_NAME,
#         expected_running_tasks=5,
#         to_options=options,
#         to_version=None,
#     )

#     # Get the current service state to verify roles have applied.
#     service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
#     current_task_roles = service_roles["task-roles"]

#     # We must have some role!
#     assert len(current_task_roles) > 0
#     assert len(current_task_roles) == 5

#     assert LEGACY_ROLE not in current_task_roles.values()
#     assert ENFORCED_ROLE in current_task_roles.values()

#     # Ensure we're not MULTI_ROLE, and only using the enforced-role.
#     assert service_roles["framework-roles"] is None
#     assert service_roles["framework-role"] == ENFORCED_ROLE


# @pytest.mark.quota_upgrade
# @pytest.mark.dcos_min_version("1.14")
# @pytest.mark.sanity
# def test_more_pods_disable_legacy_role_post_update():
#     # Ensure we can scale out more still with legacy role disabled.
#     options = {
#         "service": {
#             "name": SERVICE_NAME,
#             "service_role": ENFORCED_ROLE,
#             "subscribe_legacy_role": False,
#         },
#         "hello": {"count": 3},
#         "world": {"count": 4},
#     }

#     sdk_upgrade.update_or_upgrade_or_downgrade(
#         config.PACKAGE_NAME,
#         SERVICE_NAME,
#         expected_running_tasks=7,
#         to_options=options,
#         to_version=None,
#     )

#     # Get the current service state to verify roles have applied.
#     service_roles = sdk_utils.get_service_roles(SERVICE_NAME)
#     current_task_roles = service_roles["task-roles"]

#     # We must have some role!
#     assert len(current_task_roles) > 0
#     assert len(current_task_roles) == 7

#     assert LEGACY_ROLE not in current_task_roles.values()
#     assert ENFORCED_ROLE in current_task_roles.values()

#     # Ensure we're MULTI_ROLE
#     assert service_roles["framework-roles"] is None
#     assert service_roles["framework-role"] == ENFORCED_ROLE
