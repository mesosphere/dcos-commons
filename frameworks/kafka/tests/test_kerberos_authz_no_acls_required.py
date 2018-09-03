import logging

import pytest

import sdk_auth
import sdk_cmd
import sdk_install
import sdk_utils

from tests import auth
from tests import client
from tests import config


log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def kerberos(configure_security):
    try:
        kerberos_env = sdk_auth.KerberosEnvironment()

        principals = auth.get_service_principals(config.SERVICE_NAME, kerberos_env.get_realm())
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope="module", autouse=True)
def kafka_server(kerberos):
    """
    A pytest fixture that installs a Kerberized kafka service.

    On teardown, the service is uninstalled.
    """

    super_principal = "super"

    service_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                },
                "authorization": {
                    "enabled": True,
                    "super_users": "User:{}".format(super_principal),
                    "allow_everyone_if_no_acl_found": True,
                },
            },
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
        )

        yield {
            **service_options,
            **{"package_name": config.PACKAGE_NAME, "super_principal": super_principal},
        }
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope="module", autouse=True)
def kafka_client(kerberos):
    try:
        kafka_client = client.KafkaClient(
            "kafka-client", config.PACKAGE_NAME, config.SERVICE_NAME, kerberos
        )
        kafka_client.install()

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_authz_acls_not_required(
    kafka_client: client.KafkaClient, kafka_server: dict, kerberos: sdk_auth.KerberosEnvironment
):

    topic_name = "authz.test"
    sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "topic create {}".format(topic_name),
    )

    kafka_client.connect()

    # Since no ACLs are specified, all users can read and write.
    kafka_client.check_grant_of_permissions(["authorized", "unauthorized", "super"], topic_name)

    log.info("Writing and reading: Adding acl for authorized user")
    kafka_client.add_acls("authorized", topic_name)

    # After adding ACLs the authorized user and super user should still have access to the topic.
    kafka_client.check_grant_of_permissions(["authorized", "super"], topic_name)
    kafka_client.check_lack_of_permissions(["unauthorized"], topic_name)
