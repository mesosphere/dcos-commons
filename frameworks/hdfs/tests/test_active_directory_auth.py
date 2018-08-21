import os
import logging
import pytest

import sdk_auth
import sdk_install
import sdk_marathon
import sdk_utils

from security import kerberos as krb5

from tests import config
from tests import auth


ACTIVE_DIRECTORY_ENVVAR = "TESTING_ACTIVE_DIRECTORY_SERVER"


def is_active_directory_enabled():
    return ACTIVE_DIRECTORY_ENVVAR in os.environ


pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"), reason="TLS tests require DC/OS 1.10+"
    ),
    pytest.mark.skipif(
        not is_active_directory_enabled(),
        reason="This test requires TESTING_ACTIVE_DIRECTORY_SERVER to be set",
    )
]


log = logging.getLogger(__name__)


class ActiveDirectoryKerberos(sdk_auth.KerberosEnvironment):
    def __init__(self, keytab_id):
        self.keytab_id = keytab_id
        self.ad_server = os.environ.get(ACTIVE_DIRECTORY_ENVVAR)

    def get_host(self):
        return self.ad_server

    @staticmethod
    def get_port():
        return 88

    @staticmethod
    def get_realm():
        return "AD.MESOSPHERE.COM"

    def get_keytab_path(self):
        return "__dcos_base64__{}_keytab".format(self.keytab_id)

    def get_principal(self, user: str) -> str:
        return "{}@{}".format(user, self.get_realm())

    @staticmethod
    def cleanup():
        pass


@pytest.fixture(scope="module", autouse=True)
def kerberos(configure_security):
    try:
        kerberos_env = ActiveDirectoryKerberos(config.SERVICE_NAME)
        yield kerberos_env
    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope="module", autouse=True)
def hdfs_server(kerberos):
    """
    A pytest fixture that installs a Kerberized HDFS service.

    On teardown, the service is uninstalled.
    """
    service_options = {
        "service": {
            "name": config.SERVICE_NAME,
            "security": {
                "kerberos": {
                    "enabled": True,
                    "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
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


@pytest.mark.auth
@pytest.mark.sanity
def test_user_can_auth_and_write_and_read(hdfs_client, kerberos):
    sdk_auth.kinit(
        hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("hdfs")
    )

    test_filename = config.get_unique_filename("test_active_directory_auth_write_read")
    config.write_data_to_hdfs(test_filename)
    config.read_data_from_hdfs(test_filename)


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

    test_filename = "{}/{}".format(alice_dir, config.get_unique_filename("test_active_directory_auth_user_permissions"))

    # alice has read/write access to her directory
    sdk_auth.kdestroy(hdfs_client["id"])
    sdk_auth.kinit(
        hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("alice")
    )

    config.write_data_to_hdfs(test_filename)
    config.read_data_from_hdfs(test_filename)
    _, stdout, _ = config.list_files_in_hdfs(alice_dir)
    assert test_filename in stdout

    # bob doesn't have read/write access to alice's directory
    sdk_auth.kdestroy(hdfs_client["id"])
    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("bob"))

    config.write_data_to_hdfs(test_filename, expect_failure_message="put: Permission denied: user=bob")
    config.read_data_from_hdfs(test_filename, expect_failure_message="cat: Permission denied: user=bob")
