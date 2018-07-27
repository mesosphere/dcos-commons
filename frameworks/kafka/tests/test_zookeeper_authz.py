"""
This module tests the interaction of Kafka with Zookeeper with authorization enabled
"""
import logging
import pytest

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_security
import sdk_utils

from security import kerberos as krb5

from tests import auth
from tests import client
from tests import config


log = logging.getLogger(__name__)


pytestmark = [pytest.mark.skipif(sdk_utils.is_open_dcos(),
                                 reason="Feature only supported in DC/OS EE"),
              pytest.mark.skipif(sdk_utils.dcos_version_less_than("1.10"),
                                 reason="Kerberos tests require DC/OS 1.10 or higher")]


def get_zookeeper_principals(service_name: str, realm: str) -> list:
    primaries = ["zookeeper", ]

    tasks = [
        "zookeeper-0-server",
        "zookeeper-1-server",
        "zookeeper-2-server",
    ]
    instances = map(lambda task: sdk_hosts.autoip_host(service_name, task), tasks)

    principals = krb5.generate_principal_list(primaries, instances, realm)
    return principals


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security):
    try:
        kerberos_env = sdk_auth.KerberosEnvironment()

        principals = auth.get_service_principals(config.SERVICE_NAME,
                                                 kerberos_env.get_realm())
        principals.extend(get_zookeeper_principals(config.ZOOKEEPER_SERVICE_NAME,
                                                   kerberos_env.get_realm()))

        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module')
def zookeeper_server(kerberos):
    service_options = {
        "service": {
            "name": config.ZOOKEEPER_SERVICE_NAME,
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {
                        "hostname": kerberos.get_host(),
                        "port": int(kerberos.get_port())
                    },
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        }
    }

    zk_account = "kafka-zookeeper-service-account"
    zk_secret = "kakfa-zookeeper-secret"

    if sdk_utils.is_strict_mode():
        service_options = sdk_utils.merge_dictionaries({
            'service': {
                'service_account': zk_account,
                'service_account_secret': zk_secret,
            }
        }, service_options)

    try:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        service_account_info = sdk_security.setup_security(config.ZOOKEEPER_SERVICE_NAME,
                                                           linux_user="nobody",
                                                           service_account=zk_account,
                                                           service_account_secret=zk_secret)
        sdk_install.install(
            config.ZOOKEEPER_PACKAGE_NAME,
            config.ZOOKEEPER_SERVICE_NAME,
            config.ZOOKEEPER_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
            insert_strict_options=False)

        yield {**service_options, **{"package_name": config.ZOOKEEPER_PACKAGE_NAME}}

    finally:
        sdk_install.uninstall(config.ZOOKEEPER_PACKAGE_NAME, config.ZOOKEEPER_SERVICE_NAME)
        sdk_security.cleanup_security(config.ZOOKEEPER_SERVICE_NAME, service_account_info)


@pytest.fixture(scope='module', autouse=True)
def kafka_client(kerberos):
    try:
        kafka_client = client.KafkaClient("kafka-client")
        kafka_client.install(kerberos)

        yield kafka_client
    finally:
        kafka_client.uninstall()


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.sanity
def test_authz_acls_required(kafka_client: client.KafkaClient, zookeeper_server, kerberos):
    try:
        zookeeper_dns = sdk_cmd.svc_cli(zookeeper_server["package_name"],
                                        zookeeper_server["service"]["name"],
                                        "endpoint clientport", json=True)["dns"]

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        service_options = {
            "service": {
                "name": config.SERVICE_NAME,
                "security": {
                    "kerberos": {
                        "enabled": True,
                        "enabled_for_zookeeper": True,
                        "kdc": {
                            "hostname": kerberos.get_host(),
                            "port": int(kerberos.get_port())
                        },
                        "realm": kerberos.get_realm(),
                        "keytab_secret": kerberos.get_keytab_path(),
                    },
                    "authorization": {
                        "enabled": True,
                        "super_users": "User:{}".format("super")
                    }
                }
            },
            "kafka": {
                "kafka_zookeeper_uri": ",".join(zookeeper_dns)
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

        # Clear the ACLs
        kafka_client.remove_acls("authorized", kafka_server, topic_name)

        # Since no ACLs are specified, only the super user can read and write
        for user in ["super", ]:
            log.info("Checking write / read permissions for user=%s", user)
            write_success, read_successes, _ = kafka_client.can_write_and_read(user, kafka_server, topic_name, kerberos)
            assert write_success, "Write failed (user={})".format(user)
            assert read_successes, "Read failed (user={}): " \
                                   "MESSAGES={} " \
                                   "read_successes={}".format(user,
                                                              kafka_client.MESSAGES,
                                                              read_successes)

        for user in ["authorized", "unauthorized", ]:
            log.info("Checking lack of write / read permissions for user=%s", user)
            write_success, _, read_messages = kafka_client.can_write_and_read(user, kafka_server, topic_name, kerberos)
            assert not write_success, "Write not expected to succeed (user={})".format(user)
            assert auth.is_not_authorized(read_messages), "Unauthorized expected (user={}".format(user)

        log.info("Writing and reading: Adding acl for authorized user")
        kafka_client.add_acls("authorized", kafka_server, topic_name)

        # After adding ACLs the authorized user and super user should still have access to the topic.
        for user in ["authorized", "super"]:
            log.info("Checking write / read permissions for user=%s", user)
            write_success, read_successes, _ = kafka_client.can_write_and_read(user, kafka_server, topic_name, kerberos)
            assert write_success, "Write failed (user={})".format(user)
            assert read_successes, "Read failed (user={}): " \
                                   "MESSAGES={} " \
                                   "read_successes={}".format(user,
                                                              kafka_client.MESSAGES,
                                                              read_successes)

        for user in ["unauthorized", ]:
            log.info("Checking lack of write / read permissions for user=%s", user)
            write_success, _, read_messages = kafka_client.can_write_and_read(user, kafka_server, topic_name, kerberos)
            assert not write_success, "Write not expected to succeed (user={})".format(user)
            assert auth.is_not_authorized(read_messages), "Unauthorized expected (user={}".format(user)

    finally:
        # Ensure that we clean up the ZK state.
        kafka_client.remove_acls("authorized", kafka_server, topic_name)

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.dcos_min_version('1.10')
@pytest.mark.ee_only
@pytest.mark.sanity
def test_authz_acls_not_required(kafka_client: client.KafkaClient, zookeeper_server, kerberos):
    try:
        zookeeper_dns = sdk_cmd.svc_cli(zookeeper_server["package_name"],
                                        zookeeper_server["service"]["name"],
                                        "endpoint clientport", json=True)["dns"]

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        service_options = {
            "service": {
                "name": config.SERVICE_NAME,
                "security": {
                    "kerberos": {
                        "enabled": True,
                        "enabled_for_zookeeper": True,
                        "kdc": {
                            "hostname": kerberos.get_host(),
                            "port": int(kerberos.get_port())
                        },
                        "realm": kerberos.get_realm(),
                        "keytab_secret": kerberos.get_keytab_path(),
                    },
                    "authorization": {
                        "enabled": True,
                        "super_users": "User:{}".format("super"),
                        "allow_everyone_if_no_acl_found": True
                    }
                }
            },
            "kafka": {
                "kafka_zookeeper_uri": ",".join(zookeeper_dns)
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

        # Clear the ACLs
        kafka_client.remove_acls("authorized", kafka_server, topic_name)

        # Since no ACLs are specified, all users can read and write.
        for user in ["authorized", "unauthorized", "super", ]:
            log.info("Checking write / read permissions for user=%s", user)
            write_success, read_successes, _ = kafka_client.can_write_and_read(user, kafka_server, topic_name, kerberos)
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
            write_success, read_successes, _ = kafka_client.can_write_and_read(user, kafka_server, topic_name, kerberos)
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
                                                                              kerberos)
            assert not write_success, "Write not expected to succeed (user={})".format(user)
            assert auth.is_not_authorized(read_messages), "Unauthorized expected (user={}".format(user)

    finally:
        # Ensure that we clean up the ZK state.
        kafka_client.remove_acls("authorized", kafka_server, topic_name)

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
