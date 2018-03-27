import logging

import pytest

import sdk_cmd
import sdk_install
import sdk_utils

from security import transport_encryption

from tests import client
from tests import config
from tests import auth


log = logging.getLogger(__name__)


pytestmark = pytest.mark.skipif(sdk_utils.is_open_dcos(),
                                reason='Feature only supported in DC/OS EE')


@pytest.fixture(scope='module', autouse=True)
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        name = config.SERVICE_NAME
        service_account_info = transport_encryption.setup_service_account(name)

        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(config.SERVICE_NAME,
                                                     service_account_info)


@pytest.fixture(scope='module', autouse=True)
def kafka_client():
    try:
        kafka_client = client.KafkaClient("kafka-client")
        kafka_client.install()

        # TODO: This flag should be set correctly.
        kafka_client._is_tls = True

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.fixture(scope='module', autouse=True)
def setup_principals(kafka_client: client.KafkaClient):
    client_id = kafka_client.get_id()

    transport_encryption.create_tls_artifacts(
        cn="kafka-tester",
        task=client_id)
    transport_encryption.create_tls_artifacts(
        cn="authorized",
        task=client_id)
    transport_encryption.create_tls_artifacts(
        cn="unauthorized",
        task=client_id)
    transport_encryption.create_tls_artifacts(
        cn="super",
        task=client_id)


@pytest.mark.dcos_min_version('1.10')
@pytest.mark.ee_only
@pytest.mark.sanity
def test_authn_client_can_read_and_write(kafka_client: client.KafkaClient, service_account, setup_principals):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        service_options = {
            "service": {
                "name": config.SERVICE_NAME,
                "service_account": service_account["name"],
                "service_account_secret": service_account["secret"],
                "security": {
                    "transport_encryption": {
                        "enabled": True
                    },
                    "ssl_authentication": {
                        "enabled": True
                    }
                }
            }
        }
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_options)

        kafka_server = {**service_options, **{"package_name": config.PACKAGE_NAME}}

        topic_name = "tls.topic"
        sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                        "topic create {}".format(topic_name),
                        json=True)

        kafka_client.connect(kafka_server)

        user = "kafka-tester"
        write_success, read_successes, _ = kafka_client.can_write_and_read(user,
                                                                           kafka_server,
                                                                           topic_name,
                                                                           None)

        assert write_success, "Write failed (user={})".format(user)
        assert read_successes, "Read failed (user={}): " \
                               "MESSAGES={} " \
                               "read_successes={}".format(user,
                                                          kafka_client.MESSAGES,
                                                          read_successes)

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.10')
@pytest.mark.ee_only
@pytest.mark.sanity
def test_authz_acls_required(kafka_client: client.KafkaClient, service_account, setup_principals):

    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        service_options = {
            "service": {
                "name": config.SERVICE_NAME,
                "service_account": service_account["name"],
                "service_account_secret": service_account["secret"],
                "security": {
                    "transport_encryption": {
                        "enabled": True
                    },
                    "ssl_authentication": {
                        "enabled": True
                    },
                    "authorization": {
                        "enabled": True,
                        "super_users": "User:{}".format("super")
                    }
                }
            }
        }
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_options)

        kafka_server = {**service_options, **{"package_name": config.PACKAGE_NAME}}

        topic_name = "authz.test"
        sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                        "topic create {}".format(topic_name),
                        json=True)

        kafka_client.connect(kafka_server)

        # Since no ACLs are specified, only the super user can read and write
        for user in ["super", ]:
            log.info("Checking write / read permissions for user=%s", user)
            write_success, read_successes, _ = kafka_client.can_write_and_read(user, kafka_server, topic_name, None)
            assert write_success, "Write failed (user={})".format(user)
            assert read_successes, "Read failed (user={}): " \
                                   "MESSAGES={} " \
                                   "read_successes={}".format(user,
                                                              kafka_client.MESSAGES,
                                                              read_successes)

        for user in ["authorized", "unauthorized", ]:
            log.info("Checking lack of write / read permissions for user=%s", user)
            write_success, _, read_messages = kafka_client.can_write_and_read(user, kafka_server, topic_name, None)
            assert not write_success, "Write not expected to succeed (user={})".format(user)
            assert auth.is_not_authorized(read_messages), "Unauthorized expected (user={}".format(user)

        log.info("Writing and reading: Adding acl for authorized user")
        kafka_client.add_acls("authorized", kafka_server, topic_name)

        # After adding ACLs the authorized user and super user should still have access to the topic.
        for user in ["authorized", "super"]:
            log.info("Checking write / read permissions for user=%s", user)
            write_success, read_successes, _ = kafka_client.can_write_and_read(user, kafka_server, topic_name, None)
            assert write_success, "Write failed (user={})".format(user)
            assert read_successes, "Read failed (user={}): " \
                                   "MESSAGES={} " \
                                   "read_successes={}".format(user,
                                                              kafka_client.MESSAGES,
                                                              read_successes)

        for user in ["unauthorized", ]:
            log.info("Checking lack of write / read permissions for user=%s", user)
            write_success, _, read_messages = kafka_client.can_write_and_read(user, kafka_server, topic_name, None)
            assert not write_success, "Write not expected to succeed (user={})".format(user)
            assert auth.is_not_authorized(read_messages), "Unauthorized expected (user={}".format(user)

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.10')
@pytest.mark.ee_only
@pytest.mark.sanity
def test_authz_acls_not_required(kafka_client, service_account, setup_principals):

    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        service_options = {
            "service": {
                "name": config.SERVICE_NAME,
                "service_account": service_account["name"],
                "service_account_secret": service_account["secret"],
                "security": {
                    "transport_encryption": {
                        "enabled": True
                    },
                    "ssl_authentication": {
                        "enabled": True
                    },
                    "authorization": {
                        "enabled": True,
                        "super_users": "User:{}".format("super"),
                        "allow_everyone_if_no_acl_found": True
                    }
                }
            }
        }
        config.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_BROKER_COUNT,
            additional_options=service_options)

        kafka_server = {**service_options, **{"package_name": config.PACKAGE_NAME}}

        topic_name = "authz.test"
        sdk_cmd.svc_cli(kafka_server["package_name"], kafka_server["service"]["name"],
                        "topic create {}".format(topic_name),
                        json=True)

        kafka_client.connect(kafka_server)

        # Since no ACLs are specified, all users can read and write.
        for user in ["authorized", "unauthorized", "super", ]:
            log.info("Checking write / read permissions for user=%s", user)
            write_success, read_successes, _ = kafka_client.can_write_and_read(user, kafka_server, topic_name, None)
            assert write_success, "Write failed (user={})".format(user)
            assert read_successes, "Read failed (user={}): " \
                                   "MESSAGES={} " \
                                   "read_successes={}".format(user,
                                                              kafka_client.MESSAGES,
                                                              read_successes)

        log.info("Writing and reading: Adding acl for authorized user")
        kafka_client.add_acls("authorized", kafka_server, topic_name)

        # After adding ACLs the authorized user and super user should still have access to the topic.
        for user in ["authorized", "super", ]:
            log.info("Checking write / read permissions for user=%s", user)
            write_success, read_successes, _ = kafka_client.can_write_and_read(user, kafka_server, topic_name, None)
            assert write_success, "Write failed (user={})".format(user)
            assert read_successes, "Read failed (user={}): " \
                                   "MESSAGES={} " \
                                   "read_successes={}".format(user,
                                                              kafka_client.MESSAGES,
                                                              read_successes)

        for user in ["unauthorized", ]:
            log.info("Checking lack of write / read permissions for user=%s", user)
            write_success, _, read_messages = kafka_client.can_write_and_read(user,
                                                                              kafka_server,
                                                                              topic_name,
                                                                              None)
            assert not write_success, "Write not expected to succeed (user={})".format(user)
            assert auth.is_not_authorized(read_messages), "Unauthorized expected (user={}".format(user)

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


def write_to_topic(cn: str, task: str, topic: str, message: str) -> bool:

    return auth.write_to_topic(cn, task, topic, message,
                               auth.get_ssl_client_properties(cn, False),
                               environment=None)


def read_from_topic(cn: str, task: str, topic: str, messages: int) -> str:

    return auth.read_from_topic(cn, task, topic, messages,
                                auth.get_ssl_client_properties(cn, False),
                                environment=None)
