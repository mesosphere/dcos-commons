import logging
import pytest

import sdk_auth
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_utils

from security import kerberos as krb5
from security import transport_encryption

from tests import auth
from tests import config


log = logging.getLogger(__name__)


pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"),
        reason="TLS tests require DC/OS 1.10+"
    ),
]


@pytest.fixture(scope="module", autouse=True)
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        service_account_info = transport_encryption.setup_service_account(config.SERVICE_NAME)
        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(config.SERVICE_NAME, service_account_info)


@pytest.fixture(scope="module", autouse=True)
def kerberos(configure_security):
    try:
        principals = auth.get_service_principals(
            config.SERVICE_NAME, sdk_auth.REALM, sdk_hosts.get_crypto_id_domain()
        )

        kerberos_env = sdk_auth.KerberosEnvironment()
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope="module", autouse=True)
def hdfs_server(kerberos, service_account):
    """
    A pytest fixture that installs a Kerberized HDFS service.
    On teardown, the service is uninstalled.
    """
    service_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {
                "custom_domain": sdk_hosts.get_crypto_id_domain(),
                "kerberos": {
                    "enabled": True,
                    "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                },
                "transport_encryption": {"enabled": True},
            },
        },
        "hdfs": {"security_auth_to_local": auth.get_principal_to_user_mapping()},
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
        )

        yield {**service_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.fixture(scope="module", autouse=True)
def hdfs_client(kerberos, hdfs_server):
    try:
        client = config.get_hdfs_client_app(hdfs_server["service"]["name"], kerberos)
        sdk_marathon.install_app(client)
        krb5.write_krb5_config_file(client["id"], "/etc/krb5.conf", kerberos)
        yield client
    finally:
        sdk_marathon.destroy_app(client["id"])


# TODO(elezar) Is there a better way to determine this?
DEFAULT_JOURNAL_NODE_TLS_PORT = 8481
DEFAULT_NAME_NODE_TLS_PORT = 9003
DEFAULT_DATA_NODE_TLS_PORT = 9006


@pytest.mark.tls
@pytest.mark.sanity
@pytest.mark.parametrize(
    "node_type,port",
    [
        ("journal", DEFAULT_JOURNAL_NODE_TLS_PORT),
        ("name", DEFAULT_NAME_NODE_TLS_PORT),
        ("data", DEFAULT_DATA_NODE_TLS_PORT),
    ],
)
def test_verify_https_ports(hdfs_client, node_type, port):
    """
    Verify that HTTPS port is open name, journal and data node types.
    """

    task_id = "{}-0-node".format(node_type)
    host = sdk_hosts.custom_host(
        config.SERVICE_NAME, task_id, sdk_hosts.get_crypto_id_domain(), port
    )

    ca_bundle = transport_encryption.fetch_dcos_ca_bundle(hdfs_client["id"])

    ok, stdout, stderr = config.run_client_command("curl -v --cacert {} https://{}".format(ca_bundle, host))
    assert ok

    assert "server certificate verification OK" in stderr
    assert "common name: {}.{} (matched)".format(task_id, config.SERVICE_NAME) in stderr

    # In the Kerberos case we expect a 401 error
    assert "401 Authentication required" in stdout


@pytest.mark.auth
@pytest.mark.sanity
def test_user_can_auth_and_write_and_read(hdfs_client, kerberos):
    sdk_auth.kinit(
        hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("hdfs")
    )

    test_filename = config.get_unique_filename("test_ssl_kerberos_auth_write_read")
    config.hdfs_client_write_data(test_filename)
    config.hdfs_client_read_data(test_filename)


@pytest.mark.auth
@pytest.mark.sanity
def test_users_have_appropriate_permissions(hdfs_client, kerberos):
    # "hdfs" is a superuser
    sdk_auth.kinit(
        hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("hdfs")
    )

    alice_dir = "/users/alice"
    config.run_client_command(" && ".join([
        config.hdfs_command(c) for c in [
            "mkdir -p {}".format(alice_dir),
            "chown alice:users {}".format(alice_dir),
            "chmod 700 {}".format(alice_dir),
        ]
    ]))

    test_filename = "{}/{}".format(alice_dir, config.get_unique_filename("test_ssl_kerberos_auth_user_permissions"))

    # alice has read/write access to her directory
    sdk_auth.kdestroy(hdfs_client["id"])
    sdk_auth.kinit(
        hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("alice")
    )

    config.hdfs_client_write_data(test_filename)
    config.hdfs_client_read_data(test_filename)
    _, stdout, _ = config.hdfs_client_list_files(alice_dir)
    assert test_filename in stdout

    # bob doesn't have read/write access to alice's directory
    sdk_auth.kdestroy(hdfs_client["id"])
    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("bob"))

    config.hdfs_client_write_data(test_filename, expect_failure_message="put: Permission denied: user=bob")
    config.hdfs_client_read_data(test_filename, expect_failure_message="cat: Permission denied: user=bob")
