"""*************************************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE SHOULD ALSO BE APPLIED TO
sdk_service IN ANY OTHER PARTNER REPOS
****************************************************************************************************

"""
from typing import Any, Dict

import sdk_upgrade


DEFAULT_TIMEOUT_SECONDS = 10 * 60  # 10 minutes


def update_configuration(
    package_name: str,
    service_name: str,
    configuration: Dict[str, Any],
    expected_task_count: int,
    wait_for_deployment: bool=True,
    timeout_seconds: int=DEFAULT_TIMEOUT_SECONDS,
) -> None:
    sdk_upgrade.update_or_upgrade_or_downgrade(
        package_name=package_name,
        service_name=service_name,
        to_package_version=None,
        additional_options=configuration,
        expected_running_tasks=expected_task_count,
        wait_for_deployment=wait_for_deployment,
        timeout_seconds=timeout_seconds,
    )
