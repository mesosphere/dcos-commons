"""Utilities relating to installation of external volume providers

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_external_volumes IN ANY OTHER PARTNER REPOS
************************************************************************
"""

import logging

import sdk_agents
import sdk_install
import sdk_plan
import sdk_security
from sdk_install import PackageVersion
from typing import Iterator

log = logging.getLogger(__name__)

SLEEP_INTERVAL = 60 * 1  # Sleep interval in seconds.
EXTERNAL_VOLUMES_SERVICE_NAME = "portworx"
PORTWORX_IMAGE_VERSION = "portworx/px-enterprise:2.5.8"


def external_volumes_session() -> Iterator[None]:

    log.info("Configuring external volumes")

    # Marathon needs to be able to run the service as root user, grant it this permission.
    sdk_security.grant_marathon_root_user()

    # Install service on private agents.
    num_private_agents = len(sdk_agents.get_private_agents())

    sdk_security.setup_security(
        service_name=EXTERNAL_VOLUMES_SERVICE_NAME,
        linux_user="root",
        service_account=EXTERNAL_VOLUMES_SERVICE_NAME,
        service_account_secret=EXTERNAL_VOLUMES_SERVICE_NAME + "-secret",
    )

    service_options = {
        "service": {
            "name": EXTERNAL_VOLUMES_SERVICE_NAME,
            "principal": EXTERNAL_VOLUMES_SERVICE_NAME,
            "secret_name": EXTERNAL_VOLUMES_SERVICE_NAME + "-secret",
        },
        "node": {
            "portworx_image": PORTWORX_IMAGE_VERSION,
            "internal_kvdb": True,
            "count": num_private_agents,
        },
    }

    # Number of private agents when using internal_kvdb.
    expected_running_tasks = num_private_agents

    log.info(
        "Installing {} external volume provider on {} private agents.".format(
            EXTERNAL_VOLUMES_SERVICE_NAME, num_private_agents
        )
    )

    try:
        sdk_install.install(
            package_name=EXTERNAL_VOLUMES_SERVICE_NAME,
            service_name=EXTERNAL_VOLUMES_SERVICE_NAME,
            additional_options=service_options,
            expected_running_tasks=expected_running_tasks,
            wait_for_deployment=True,
            package_version=PackageVersion.LATEST_UNIVERSE,
        )

        # Ensure the deployment plan is complete
        sdk_plan.wait_for_completed_deployment(
            service_name=EXTERNAL_VOLUMES_SERVICE_NAME, timeout_seconds=SLEEP_INTERVAL
        )

        # Ensure the recovery plan is not running.
        sdk_plan.wait_for_completed_recovery(
            service_name=EXTERNAL_VOLUMES_SERVICE_NAME, timeout_seconds=SLEEP_INTERVAL
        )

        log.info(
            "External volume provider {} installed successfully.".format(
                EXTERNAL_VOLUMES_SERVICE_NAME
            )
        )

        # Resume execution of tests.
        yield
    finally:
        log.info(
            "Teardown of external volume provider {} initiated.".format(
                EXTERNAL_VOLUMES_SERVICE_NAME
            )
        )

        sdk_install.uninstall(
            package_name=EXTERNAL_VOLUMES_SERVICE_NAME, service_name=EXTERNAL_VOLUMES_SERVICE_NAME
        )

        sdk_security.delete_service_account(
            service_account_name=EXTERNAL_VOLUMES_SERVICE_NAME,
            service_account_secret=EXTERNAL_VOLUMES_SERVICE_NAME + "-secret",
        )

        log.info(
            "Completd Teardown of external volume provider {}.".format(
                EXTERNAL_VOLUMES_SERVICE_NAME
            )
        )
