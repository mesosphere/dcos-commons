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


TOPIC_NAME = "authzTest"


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
def zookeeper_service(kerberos):
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
            package_version=sdk_install.PackageVersion.LATEST_UNIVERSE.value,
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
    allow_access_if_no_acl: bool,
    kerberos: sdk_auth.KerberosEnvironment,
    zookeeper_dns: typing.List[str],
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
                "authorization": {
                    "enabled": True,
                    "super_users": "User:{}".format("super"),
                    "allow_everyone_if_no_acl_found": allow_access_if_no_acl,
                },
            },
        },
        "kafka": {"kafka_zookeeper_uri": ",".join(zookeeper_dns)},
    }
    return service_options


def _configure_kafka_cluster(
    kafka_client: client.KafkaClient, zookeeper_service: typing.Dict, allow_access_if_no_acl: bool
) -> client.KafkaClient:
    zookeeper_dns = sdk_networks.get_endpoint(
        zookeeper_service["package_name"], zookeeper_service["service"]["name"], "clientport"
    )["dns"]

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    service_options = _get_service_options(
        allow_access_if_no_acl, kafka_client.kerberos, zookeeper_dns
    )

    config.install(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_BROKER_COUNT,
        additional_options=service_options,
    )

    kafka_server = {**service_options, **{"package_name": config.PACKAGE_NAME}}

    sdk_cmd.svc_cli(
        kafka_server["package_name"],
        kafka_server["service"]["name"],
        "topic create {}".format(TOPIC_NAME),
    )

    kafka_client.connect()

    # Clear the ACLs
    kafka_client.remove_acls("authorized", TOPIC_NAME)
    return kafka_client


def _test_permissions(
    kafka_client: client.KafkaClient,
    zookeeper_service: typing.Dict,
    allow_access_if_no_acl: bool,
    permission_test: typing.Callable[[client.KafkaClient, str], None],
):
    try:
        checker = _configure_kafka_cluster(kafka_client, zookeeper_service, allow_access_if_no_acl)
        permission_test(checker, TOPIC_NAME)
    finally:
        # Ensure that we clean up the ZK state.
        kafka_client.remove_acls("authorized", TOPIC_NAME)

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_authz_acls_required(kafka_client: client.KafkaClient, zookeeper_service: typing.Dict):
    def permission_test(c: client.KafkaClient, topic_name: str):
        # Since no ACLs are specified, only the super user can read and write
        c.check_users_can_read_and_write(["super"], topic_name)
        c.check_users_are_not_authorized_to_read_and_write(
            ["authorized", "unauthorized"], topic_name
        )

        log.info("Writing and reading: Adding acl for authorized user")
        c.add_acls("authorized", topic_name)

        # After adding ACLs the authorized user and super user should still have access to the topic.
        c.check_users_can_read_and_write(["authorized", "super"], topic_name)
        c.check_users_are_not_authorized_to_read_and_write(["unauthorized"], topic_name)

    _test_permissions(kafka_client, zookeeper_service, False, permission_test)


@pytest.mark.sanity
def test_authz_acls_not_required(kafka_client: client.KafkaClient, zookeeper_service: typing.Dict):
    def permission_test(c: client.KafkaClient, topic_name: str):
        # Since no ACLs are specified, all users can read and write.
        c.check_users_can_read_and_write(["authorized", "unauthorized", "super"], topic_name)

        log.info("Writing and reading: Adding acl for authorized user")
        c.add_acls("authorized", topic_name)

        # After adding ACLs the authorized user and super user should still have access to the topic.
        c.check_users_can_read_and_write(["authorized", "super"], topic_name)
        c.check_users_are_not_authorized_to_read_and_write(["unauthorized"], topic_name)

    _test_permissions(kafka_client, zookeeper_service, True, permission_test)
