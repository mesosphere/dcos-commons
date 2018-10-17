import pytest
import sdk_install
import sdk_security
from tests import config


ACCOUNTS = {}

@pytest.mark.scale
def test_scaling_load(service_count,
                      scenario) -> None:
    for index in range(service_count):
        print(index)
        _install_service("%s-%s-%s" % (config.PACKAGE_NAME, scenario, index),
                         scenario)


def _install_service(service_name, scenario):
    # do not wait for deploy plan to complete, all tasks to launch or marathon app deployment
    # supports rapid deployments in scale test scenario
    sdk_install.install(
        config.PACKAGE_NAME,
        service_name,
        config.DEFAULT_TASK_COUNT,
        additional_options={"service": {"name": service_name, "yaml": scenario}},
        wait_for_deployment=False,
        wait_for_all_conditions=False
    )

# def _create_service_accounts(service_name, security=None):
#     if security == DCOS_SECURITY.strict:
#         try:
#             log.info("Creating service accounts for '{}'"
#                      .format(service_name))
#             sa_name = "{}-principal".format(service_name)
#             sa_secret = "helloworld-{}-secret".format(service_name)
#             sdk_security.create_service_account(
#                 sa_name, sa_secret, service_name)
#
#             sdk_security.grant_permissions(
#                 'root', '*', sa_name)
#
#             sdk_security.grant_permissions(
#                 'root', SHARED_ROLE, sa_name)
#             end = time.time()
#             ACCOUNTS[service_name] = {}
#             ACCOUNTS[service_name]["sa_name"] = sa_name
#             ACCOUNTS[service_name]["sa_secret"] = sa_secret
#         except Exception as e:
#             log.warning("Error encountered while creating service account: {}".format(e))
#             raise e