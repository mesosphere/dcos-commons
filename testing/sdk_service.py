"""*************************************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE SHOULD ALSO BE APPLIED TO
sdk_service IN ANY OTHER PARTNER REPOS
****************************************************************************************************

"""

import sdk_upgrade


DEFAULT_TIMEOUT_SECONDS = 10 * 60  # 10 minutes


def update_configuration(
    package_name,
    service_name,
    configuration,
    expected_task_count,
    wait_for_deployment=True,
    timeout_seconds=DEFAULT_TIMEOUT_SECONDS,
):
    sdk_upgrade.update_or_upgrade_or_downgrade(
        package_name,
        service_name,
        None,
        configuration,
        expected_task_count,
        wait_for_deployment=wait_for_deployment,
        timeout_seconds=timeout_seconds,
    )
