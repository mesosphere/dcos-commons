import pytest
import logging
import sdk_install
import sdk_security
import sdk_dcos
from tests import config

log = logging.getLogger(__name__)

ACCOUNTS = {}

@pytest.mark.scale
def test_scaling_load(service_count,
                      scenario) -> None:
    """Launch a load test scenario. This does not verify the results
    of the test, but does ensure the instances and jobs were created.

    Args:
        count: number of helloworld services to install
        scenario: yaml scenario to run helloworld with (normal, crashloop) are added for this case
    """
    security_mode = sdk_dcos.get_security_mode()
    #TODO: kvish parallelize account creation and installation if time is an issue in scale tests
    for index in range(service_count):
        service_name = "{}-{}-{}".format(config.PACKAGE_NAME, scenario, index)
        _create_service_account(service_name,
                                security_mode)
        _install_service(service_name,
                         scenario,
                         security_mode)


def _install_service(service_name, scenario, security=None):
    # do not wait for deploy plan to complete, all tasks to launch or marathon app deployment
    # supports rapid deployments in scale test scenario
    options = {"service": {"name": service_name, "yaml": scenario}}
    if security == sdk_dcos.DCOS_SECURITY.strict:
        options = {"service": {"name": service_name,
                               "yaml": scenario,
                               "service_account": ACCOUNTS[service_name]["sa_name"],
                               "service_account_secret": ACCOUNTS[service_name]["sa_secret"]}}

    sdk_install.install(
        config.PACKAGE_NAME,
        service_name,
        config.DEFAULT_TASK_COUNT,
        additional_options=options,
        wait_for_deployment=False,
        wait_for_all_conditions=False
    )

def _create_service_account(service_name, security=None):
    if security == sdk_dcos.DCOS_SECURITY.strict:
        try:
            log.info("Creating service accounts for '{}'"
                     .format(service_name))
            sa_name = "{}-principal".format(service_name)
            sa_secret = "helloworld-{}-secret".format(service_name)
            sdk_security.create_service_account(
                sa_name, sa_secret)

            sdk_security.grant_permissions(
                'nobody', '*', sa_name, None)

            sdk_security.grant_permissions(
                'nobody', "{}-role".format(service_name), sa_name, None)
            ACCOUNTS[service_name] = {}
            ACCOUNTS[service_name]["sa_name"] = sa_name
            ACCOUNTS[service_name]["sa_secret"] = sa_secret
        except Exception as e:
            log.warning("Error encountered while creating service account: {}".format(e))
            raise e
