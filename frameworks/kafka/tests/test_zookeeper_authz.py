"""
This module tests the interaction of Kafka with Zookeeper with authorization enabled
"""
import logging
import pytest
import typing

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_networks
import sdk_security
import sdk_utils

from security import kerberos as krb5

from tests import auth
from tests import client
from tests import config


log = logging.getLogger(__name__)


pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"),
        reason="Kerberos tests require DC/OS 1.10 or higher",
    ),
]


def get_zookeeper_principals(service_name: str, realm: str) -> list:
    primaries = ["zookeeper"]

    tasks = ["zookeeper-0-server", "zookeeper-1-server", "zookeeper-2-server"]
    instances = map(lambda task: sdk_hosts.autoip_host(service_name, task), tasks)

    principals = krb5.generate_principal_list(primaries, instances, realm)
    return principals


@pytest.fixture(scope="module", autouse=True)
def kerberos(configure_security):
    try:
        kerberos_env = sdk_auth.KerberosEnvironment()

        principals = auth.get_service_principals(config.SERVICE_NAME, kerberos_env.get_realm())
        principals.extend(
            get_zookeeper_principals(config.ZOOKEEPER_SERVICE_NAME, kerberos_env.get_realm())
        )

        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope="module")
def zookeeper_server(kerberos):
    service_options = {
        "service": {
            "name": config.ZOOKEEPER_SERVICE_NAME,
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            },
        }
    }

    zk_account = "kafka-zookeeper-service-account"
    zk_secret = "kakfa-zookeeper-secret"

    if sdk_utils.is_strict_mode():
        service_options = sdk_utils.merge_dictionaries(
            {"service": {"service_account": zk_account, "service_account_secret": zk_secret}},
            service_options,
        )

    try:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        service_account_info = sdk_security.setup_security(
            config.ZOOKEEPER_SERVICE_NAME,
            linux_user="nobody",
            service_account=zk_account,
            service_account_secret=zk_secret,
        )
        sdk_install.install(
            config.ZOOKEEPER_PACKAGE_NAME,
            config.ZOOKEEPER_SERVICE_NAME,
            config.ZOOKEEPER_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
            insert_strict_options=False,
        )

        yield {**service_options, **{"package_name": config.ZOOKEEPER_PACKAGE_NAME}}

    finally:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        sdk_security.cleanup_security(config.ZOOKEEPER_SERVICE_NAME, service_account_info)


@pytest.fixture(scope="module", autouse=True)
def kafka_client(kerberos: sdk_auth.KerberosEnvironment):
    try:
        kafka_client = client.KafkaClient(
            "kafka-client", config.PACKAGE_NAME, config.SERVICE_NAME, kerberos
        )
        kafka_client.install()

        yield kafka_client
    finally:
        kafka_client.uninstall()


def _get_service_options(
    allow_everyone: bool, kerberos: sdk_auth.KerberosEnvironment, zookeeper_dns: str
) -> typing.Dict:
    service_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "security": {
                "kerberos": {
                    "enabled": True,
                    "enabled_for_zookeeper": True,
                    "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                },
                "authorization": {"enabled": True, "super_users": "User:{}".format("super")},
            },
        },
        "kafka": {"kafka_zookeeper_uri": ",".join(zookeeper_dns)},
    }
    if allow_everyone:
        service_options["service"]["security"]["authorization"][
            "allow_everyone_if_no_acl_found"
        ] = True
    return service_options


class PermissionCheckWrapper:
    def __init__(
        self,
        kerberos: sdk_auth.KerberosEnvironment,
        kafka_client: client.KafkaClient,
        topic_name: str,
    ) -> None:
        self.kerberos = kerberos
        self.kafka_client = kafka_client
        self.topic_name = topic_name

    def check_lack_of_permissions(self, users: typing.List[str]) -> None:
        for user in users:
            log.info("Checking lack of write / read permissions for user=%s", user)
            write_success, _, read_messages = self.kafka_client.can_write_and_read(
                user, self.topic_name
            )
            assert not write_success, "Write not expected to succeed (user={})".format(user)
            assert auth.is_not_authorized(read_messages), "Unauthorized expected (user={}".format(
                user
            )

    def check_grant_of_permissions(self, users: typing.List[str]) -> None:
        for user in users:
            log.info("Checking write / read permissions for user=%s", user)
            write_success, read_successes, _ = self.kafka_client.can_write_and_read(
                user, self.topic_name
            )
            assert write_success, "Write failed (user={})".format(user)
            assert read_successes, (
                "Read failed (user={}): "
                "MESSAGES={} "
                "read_successes={}".format(user, self.kafka_client.MESSAGES, read_successes)
            )


def _configure_kafka_cluster(
    kafka_client: client.KafkaClient, zookeeper_server: typing.Dict, allow_everyone: bool
) -> PermissionCheckWrapper:
    zookeeper_dns = sdk_networks.get_endpoint(
        zookeeper_server["package_name"], zookeeper_server["service"]["name"], "clientport"
    )["dns"]

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    service_options = _get_service_options(allow_everyone, kafka_client.kerberos, zookeeper_dns)

    config.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_BROKER_COUNT,
        additional_options=service_options,
    )

    kafka_server = {**service_options, **{"package_name": config.PACKAGE_NAME}}

    topic_name = "authz.test"
    sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "topic create {}".format(topic_name),
    )

    kafka_client.connect()

    # Clear the ACLs
    kafka_client.remove_acls("authorized", topic_name)
    return PermissionCheckWrapper(kafka_client.kerberos, kafka_client, topic_name)


def _test_permissions(
    kafka_client: client.KafkaClient,
    zookeeper_server: typing.Dict,
    allow_everyone: bool,
    permission_test: typing.Callable[[PermissionCheckWrapper], None],
):
    try:
        checker = _configure_kafka_cluster(kafka_client, zookeeper_server, allow_everyone)
        permission_test(checker)
    finally:
        # Ensure that we clean up the ZK state.
        kafka_client.remove_acls("authorized", checker.topic_name)

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_authz_acls_required(kafka_client: client.KafkaClient, zookeeper_server: typing.Dict):
    # Since no ACLs are specified, only the super user can read and write
    def permission_test(checker: PermissionCheckWrapper):
        checker.check_grant_of_permissions(["super"])
        checker.check_lack_of_permissions(["authorized", "unauthorized"])

        log.info("Writing and reading: Adding acl for authorized user")
        checker.kafka_client.add_acls("authorized", checker.topic_name)

        # After adding ACLs the authorized user and super user should still have access to the topic.
        checker.check_grant_of_permissions(["authorized", "super"])
        checker.check_lack_of_permissions(["unauthorized"])

    _test_permissions(kafka_client, zookeeper_server, False, permission_test)


@pytest.mark.sanity
def test_authz_acls_not_required(kafka_client: client.KafkaClient, zookeeper_server: typing.Dict):
    # Since no ACLs are specified, all users can read and write.
    def permission_test(checker: PermissionCheckWrapper):
        checker.check_grant_of_permissions(["authorized", "unauthorized", "super"])

        log.info("Writing and reading: Adding acl for authorized user")
        checker.kafka_client.add_acls("authorized", checker.topic_name)

        # After adding ACLs the authorized user and super user should still have access to the topic.
        checker.check_grant_of_permissions(["authorized", "super"])
        checker.check_lack_of_permissions(["unauthorized"])

    _test_permissions(kafka_client, zookeeper_server, True, permission_test)
