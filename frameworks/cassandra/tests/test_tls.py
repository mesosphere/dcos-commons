import json

import pytest
import shakedown

import sdk_cmd
import sdk_install
import sdk_networks
import sdk_plan
import sdk_security
import sdk_utils


from tests.test_utils import (
    DEFAULT_TOPIC_NAME,
    DEFAULT_PARTITION_COUNT,
    DEFAULT_BROKER_COUNT,
    PACKAGE_NAME,
    service_cli
)


@pytest.fixture(scope='module')
def dcos_security_cli():
    """
    Installs the dcos enterprise cli.
    """

    sdk_cmd.run_cli("package install --yes dcos-enterprise-cli")


@pytest.fixture(scope='module')
def service_account(dcos_security_cli):
    """
    Creates service account with `hello-world` name and yields the name.
    """
    name = 'cassandra'
    sdk_security.create_service_account(
        service_account_name=name, secret_name=name)
     # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
    sdk_cmd.run_cli(
        "security org groups add_user superusers {name}".format(name=name))
    yield name
    sdk_security.delete_service_account(
        service_account_name=name, secret_name=name)


@pytest.fixture(scope='module')
def cassandra_service_tls(service_account):
    sdk_install.install(
        PACKAGE_NAME,
        DEFAULT_BROKER_COUNT,
        service_name=service_account,
        additional_options={
            "service": {
                "secret_name": service_account,
                "principal": service_account,
                "tls": True,
            }
        }
    )

    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)

    # Wait for service health check to pass
    shakedown.service_healthy(PACKAGE_NAME)

    yield service_account

    sdk_install.uninstall(PACKAGE_NAME)
