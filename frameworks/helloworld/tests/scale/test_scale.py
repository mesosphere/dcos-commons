import pytest
import logging
import sdk_install
import sdk_security
import sdk_utils
from tests import config

log = logging.getLogger(__name__)


@pytest.mark.soak
def test_soak_load(service_name,
                   scenario) -> None:
    """Launch a soak test scenario. This does not verify the results
    of the test, but does ensure the instances were created.

    Args:
        service_name: name of the service to install as
        scenario: yaml scenario to run helloworld with (normal, crashloop) are added for this case
    """
    # Note service-names *cannot* have underscores in them.
    soak_service_name = ("{}-{}-soak".format(service_name, scenario)).replace("_", "-")
    # service-names can have '/'s in them but service account names cannot, sanitize here.
    service_account_name = soak_service_name.replace("/", "__")
    security_info = _create_service_account(service_account_name)
    _install_service(soak_service_name,
                     scenario,
                     security_info)


@pytest.mark.scale
def test_scaling_load(service_name,
                      service_count,
                      scenario) -> None:
    """Launch a load test scenario. This does not verify the results
    of the test, but does ensure the instances were created.

    Args:
        service_name: name of the service to install as
        service_count: number of helloworld services to install
        scenario: yaml scenario to run helloworld with (normal, crashloop) are added for this case
    """
    # TODO: parallelize account creation and installation if time is an issue in scale tests
    for index in range(service_count):
        # Note service-names *cannot* have underscores in them.
        scale_service_name = ("{}-{}-{}".format(service_name, scenario, index)).replace("_", "-")
        # service-names can have '/'s in them but service account names cannot, sanitize here.
        service_account_name = scale_service_name.replace("/", "__")
        security_info = _create_service_account(service_account_name)
        _install_service(scale_service_name,
                         scenario,
                         security_info)


def _install_service(service_name, scenario, security_info):
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


def _create_service_account(service_name):
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
