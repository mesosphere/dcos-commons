import pytest
import logging
import sdk_install
import sdk_security
import sdk_utils

from tests import config
from threading_utils import spawn_threads, wait_and_get_failures

log = logging.getLogger(__name__)

JOB_RUN_TIMEOUT = 10 * 60  # 10 mins


@pytest.mark.soak
def test_soak_load(service_name,
                   scenario) -> None:
    """Launch a soak test scenario. This does not verify the results
    of the test, but does ensure the instances were created.

    Args:
        service_name: name of the service to install as
        scenario: yaml scenario to run helloworld with (normal, crashloop) are added for this case
    """
    soak_service_name = "{}-{}-soak".format(service_name, scenario)
    _launch_load(soak_service_name, scenario)


@pytest.mark.scale
def test_scaling_load(service_name,
                      scenario,
                      service_count,
                      min_index,
                      max_index,
                      batch_size) -> None:
    """Launch a load test scenario in parallel if needed.
    This does not verify the results of the test, but does
    ensure the instances were created.

    Args:
        service_name: name of the service to install as
        scenario: yaml scenario to run helloworld with (normal, crashloop) are added for this case
        service_count: number of helloworld services to install
        min_index: minimum index to begin suffixes at
        max_index: maximum index to end suffixes at
        batch_size: batch size to deploy instances in
    """
    scale_service_name = "{}-{}".format(service_name, scenario)
    deployment_list = []
    if min_index == -1 or max_index == -1:
        deployment_list = ["{}-{}".format(scale_service_name, index) for index in range(0, int(service_count))]
        min_index = 0
        max_index = service_count
    else:
        deployment_list = ["{}-{}".format(scale_service_name, index) for index in range(min_index, max_index)]

    sdk_security.install_enterprise_cli()

    current = 0
    end = max_index - min_index
    for current in range(0, end, batch_size):
        # Get current batch of schedulers to deploy.
        batched_deployment_list = deployment_list[current:current + batch_size]
        # Create threads with correct arguments.
        deployment_threads = spawn_threads(batched_deployment_list,
                                           _launch_load,
                                           scenario=scenario)
        # Launch jobs.
        wait_and_get_failures(deployment_threads, timeout=JOB_RUN_TIMEOUT)


@pytest.mark.scalecleanup
def test_scaling_cleanup(service_name, scenario, service_count, min_index, max_index) -> None:
    """ Cleanup of installed hello-world service for specified scenario
    Args:
        service_name: name of the service to uninstall
        scenario: yaml scenario helloworld installed with (normal, crashloop)
        service_count: number of helloworld services to uninstall
        min_index: minimum index to begin suffixes at
        max_index: maximum index to end suffixes at
    """
    scale_service_name = "{}-{}".format(service_name, scenario)

    if min_index == -1 or max_index == -1:
        scale_cleanup_list = ["{}-{}".format(scale_service_name, index) for index in range(0, int(service_count))]
    else:
        scale_cleanup_list = ["{}-{}".format(scale_service_name, index) for index in range(min_index, max_index)]

    sdk_security.install_enterprise_cli()

    cleanup_threads = spawn_threads(scale_cleanup_list,
                                    _uninstall_service)
    # Launch jobs.
    wait_and_get_failures(cleanup_threads, timeout=JOB_RUN_TIMEOUT)


def _launch_load(service_name, scenario) -> None:
    """Launch a load test scenario. This does not verify the results
    of the test, but does ensure the instances were created.

    Args:
        service_name: name of the service to install as
        scenario: yaml scenario to run helloworld with (normal, crashloop) are added for this case
    """
    # Note service-names *cannot* have underscores in them.
    launch_service_name = service_name.replace("_", "-")
    # service-names can have '/'s in them but service account names cannot, sanitize here.
    launch_account_name = launch_service_name.replace("/", "__")
    security_info = _create_service_account(launch_account_name)
    _install_service(launch_service_name,
                     scenario,
                     security_info)


def _install_service(service_name, scenario, security_info) -> None:
    # do not wait for deploy plan to complete, all tasks to launch or marathon app deployment
    # supports rapid deployments in scale test scenario
    options = {"service": {"name": service_name, "yaml": scenario}}
    if security_info:
        options["service"]["service_account"] = security_info["name"]
        options["service"]["service_account_secret"] = security_info["secret"]

    sdk_install.install(
        config.PACKAGE_NAME,
        service_name,
        config.DEFAULT_TASK_COUNT,
        additional_options=options,
        wait_for_deployment=False,
        wait_for_all_conditions=False
    )


def _uninstall_service(service_name) -> None:
    """Uninstall specified service instance.

    Args:
        service_name: Service name or Marathon ID
        role: The role for the service to use (default is no role)
    """
    if service_name.startswith('/'):
        service_name = service_name[1:]
    # Note service-names *cannot* have underscores in them.
    service_name = service_name.replace("_", "-")
    log.info("Uninstalling {}.".format(service_name))
    sdk_install.uninstall(config.PACKAGE_NAME,
                          service_name)


def _create_service_account(service_name) -> None:
    if sdk_utils.is_strict_mode():
        try:
            log.info("Creating service accounts for '{}'"
                     .format(service_name))

            sa_name = "{}-principal".format(service_name)
            sa_secret = "{}-secret".format(service_name)
            return sdk_security.setup_security(
                service_name,
                linux_user="nobody",
                service_account=sa_name,
                service_account_secret=sa_secret,
            )

        except Exception as e:
            log.warning("Error encountered while creating service account: {}".format(e))
            raise e

    return None
