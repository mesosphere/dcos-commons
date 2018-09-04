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
        kafka_client = client.KafkaClient("kafka-client")
        kafka_client.install(kerberos)

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

    kafka_client.connect(kafka_server)

    # Since no ACLs are specified, all users can read and write.
    for user in ["authorized", "unauthorized", "super"]:
        log.info("Checking write / read permissions for user=%s", user)
        write_success, read_successes, _ = kafka_client.can_write_and_read(
            user, kafka_server, topic_name, kerberos
        )
        assert write_success, "Write failed (user={})".format(user)
        assert read_successes, (
            "Read failed (user={}): "
            "MESSAGES={} "
            "read_successes={}".format(user, kafka_client.MESSAGES, read_successes)
        )

    log.info("Writing and reading: Adding acl for authorized user")
    kafka_client.add_acls("authorized", kafka_server, topic_name)

    # After adding ACLs the authorized user and super user should still have access to the topic.
    for user in ["authorized", "super"]:
        log.info("Checking write / read permissions for user=%s", user)
        write_success, read_successes, _ = kafka_client.can_write_and_read(
            user, kafka_server, topic_name, kerberos
        )
        assert write_success, "Write failed (user={})".format(user)
        assert read_successes, (
            "Read failed (user={}): "
            "MESSAGES={} "
            "read_successes={}".format(user, kafka_client.MESSAGES, read_successes)
        )

    for user in ["unauthorized"]:
        log.info("Checking lack of write / read permissions for user=%s", user)
        write_success, _, read_messages = kafka_client.can_write_and_read(
            user, kafka_server, topic_name, kerberos
        )
        assert not write_success, "Write not expected to succeed (user={})".format(user)
        assert auth.is_not_authorized(read_messages), "Unauthorized expected (user={}".format(user)
