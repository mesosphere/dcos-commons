import logging
import pytest

import sdk_auth
import sdk_install
import sdk_utils


from security import transport_encryption


from tests import auth
from tests import client
from tests import config


log = logging.getLogger(__name__)


pytestmark = sdk_utils.dcos_ee_only


@pytest.fixture(scope="module", autouse=True)
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        name = config.SERVICE_NAME
        service_account_info = transport_encryption.setup_service_account(name)

        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(config.SERVICE_NAME, service_account_info)


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
def kafka_client(kerberos):
    try:
        kafka_client = client.KafkaClient(
            "kafka-client", config.PACKAGE_NAME, config.SERVICE_NAME, kerberos
        )
        kafka_client.install()

        # TODO: This flag should be set correctly.
        kafka_client._is_tls = True

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.fixture(scope="module", autouse=True)
def setup_principals(kafka_client: client.KafkaClient):
    client_id = kafka_client.get_id()

    transport_encryption.create_tls_artifacts(cn="client", marathon_task=client_id)
    transport_encryption.create_tls_artifacts(cn="authorized", marathon_task=client_id)
    transport_encryption.create_tls_artifacts(cn="unauthorized", marathon_task=client_id)
    transport_encryption.create_tls_artifacts(cn="super", marathon_task=client_id)


@pytest.mark.dcos_min_version("1.10")
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_authz_acls_required(
    kafka_client: client.KafkaClient, kerberos, service_account, setup_principals
):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        service_options = {
            "service": {
                "name": config.SERVICE_NAME,
                "service_account": service_account["name"],
                "service_account_secret": service_account["secret"],
                "security": {
                    "kerberos": {
                        "enabled": True,
                        "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                        "realm": kerberos.get_realm(),
                        "keytab_secret": kerberos.get_keytab_path(),
                    },
                    "transport_encryption": {"enabled": True},
                    "authorization": {"enabled": True, "super_users": "User:{}".format("super")},
                },
            }
        }
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_options,
        )

        topic_name = "authz.test"
        kafka_client.connect(config.DEFAULT_BROKER_COUNT)
        kafka_client.create_topic(topic_name)

        # Clear the ACLs
        kafka_client.remove_acls("authorized", topic_name)

        # Since no ACLs are specified, only the super user can read and write
        kafka_client.check_users_can_read_and_write(["super"], topic_name)
        kafka_client.check_users_are_not_authorized_to_read_and_write(
            ["authorized", "unauthorized"], topic_name
        )

        log.info("Writing and reading: Adding acl for authorized user")
        kafka_client.add_acls("authorized", topic_name)

        # After adding ACLs the authorized user and super user should still have access to the topic.
        kafka_client.check_users_can_read_and_write(["authorized", "super"], topic_name)
        kafka_client.check_users_are_not_authorized_to_read_and_write(["unauthorized"], topic_name)

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version("1.10")
@pytest.mark.ee_only
@pytest.mark.sanity
def test_authz_acls_not_required(
    kafka_client: client.KafkaClient, kerberos, service_account, setup_principals
):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        service_options = {
            "service": {
                "name": config.SERVICE_NAME,
                "service_account": service_account["name"],
                "service_account_secret": service_account["secret"],
                "security": {
                    "kerberos": {
                        "enabled": True,
                        "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                        "realm": kerberos.get_realm(),
                        "keytab_secret": kerberos.get_keytab_path(),
                    },
                    "transport_encryption": {"enabled": True},
                    "authorization": {
                        "enabled": True,
                        "super_users": "User:{}".format("super"),
                        "allow_everyone_if_no_acl_found": True,
                    },
                },
            }
        }

        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_options,
        )

        topic_name = "authz.test"
        kafka_client.connect(config.DEFAULT_BROKER_COUNT)
        kafka_client.create_topic(topic_name)

        # Clear the ACLs
        kafka_client.remove_acls("authorized", topic_name)

        # Since no ACLs are specified, all users can read and write.
        kafka_client.check_users_can_read_and_write(
            ["authorized", "unauthorized", "super"], topic_name
        )

        log.info("Writing and reading: Adding acl for authorized user")
        kafka_client.add_acls("authorized", topic_name)

        # After adding ACLs the authorized user and super user should still have access to the topic.
        kafka_client.check_users_can_read_and_write(["authorized", "super"], topic_name)
        kafka_client.check_users_are_not_authorized_to_read_and_write(["unauthorized"], topic_name)

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
