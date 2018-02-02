import logging
import pytest

import sdk_auth
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_utils

from tests import auth
from tests import config


log = logging.getLogger(__name__)


pytestmark = pytest.mark.skipif(sdk_utils.is_open_dcos(),
                                reason='Feature only supported in DC/OS EE')


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security):
    try:
        kerberos_env = sdk_auth.KerberosEnvironment()

        principals = auth.get_service_principals(config.FOLDERED_SERVICE_NAME,
                                                 kerberos_env.get_realm())
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()
        service_kerberos_options = {
            "service": {
                "name": config.FOLDERED_SERVICE_NAME,
                "security": {
                    "kerberos": {
                        "enabled": True,
                        "kdc": {
                            "hostname": kerberos_env.get_host(),
                            "port": int(kerberos_env.get_port())
                        },
                        "keytab_secret": kerberos_env.get_keytab_path(),
                        "realm": kerberos.get_realm()
                    }
                }
            },
            "hdfs": {
                "security_auth_to_local": auth.get_principal_to_user_mapping()
            }
        }

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.FOLDERED_SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=service_kerberos_options,
            timeout_seconds=30*60)

        yield kerberos_env

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.FOLDERED_SERVICE_NAME)
        if kerberos_env:
            kerberos_env.cleanup()


@pytest.fixture(autouse=True)
@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.smoke
@pytest.mark.sanity
def test_health_of_kerberized_hdfs():
    config.check_healthy(service_name=config.FOLDERED_SERVICE_NAME)


@pytest.fixture(scope="module", autouse=True)
@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
def kerberized_hdfs_client(kerberos):
    try:
        client_app_def = config.get_kerberized_hdfs_client_app()
        client_app_def["secrets"]["hdfs_keytab"]["source"] = kerberos.get_keytab_path()
        client_app_def["env"]["REALM"] = kerberos.get_realm()
        client_app_def["env"]["KDC_ADDRESS"] = kerberos.get_kdc_address()
        client_app_def["env"]["HDFS_SERVICE_NAME"] = config.FOLDERED_DNS_NAME
        sdk_marathon.install_app(client_app_def)
        yield client_app_def["id"]

    finally:
        sdk_marathon.destroy_app(client_app_def["id"])


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.auth
@pytest.mark.sanity
def test_user_can_auth_and_write_and_read(kerberized_hdfs_client):
    sdk_auth.kinit(kerberized_hdfs_client, keytab=config.KEYTAB, principal=kerberos.get_principal("hdfs"))

    test_filename = "test_auth_write_read"  # must be unique among tests in this suite
    write_cmd = "/bin/bash -c '{}'".format(config.hdfs_write_command(config.TEST_CONTENT_SMALL, test_filename))
    sdk_cmd.task_exec(kerberized_hdfs_client, write_cmd)

    read_cmd = "/bin/bash -c '{}'".format(config.hdfs_read_command(test_filename))
    _, stdout, _ = sdk_cmd.task_exec(kerberized_hdfs_client, read_cmd)
    assert stdout == config.TEST_CONTENT_SMALL


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.auth
@pytest.mark.sanity
@pytest.mark.skip(reason="HDFS-493")
def test_users_have_appropriate_permissions(kerberized_hdfs_client):
    # "hdfs" is a superuser
    sdk_auth.kinit(kerberized_hdfs_client, keytab=config.KEYTAB, principal=kerberos.get_principal("hdfs"))

    log.info("Creating directory for alice")
    make_user_directory_cmd = config.hdfs_command("mkdir -p /users/alice")
    sdk_cmd.task_exec(kerberized_hdfs_client, make_user_directory_cmd)

    change_ownership_cmd = config.hdfs_command("chown alice:users /users/alice")
    sdk_cmd.task_exec(kerberized_hdfs_client, change_ownership_cmd)

    change_permissions_cmd = config.hdfs_command("chmod 700 /users/alice")
    sdk_cmd.task_exec(kerberized_hdfs_client, change_permissions_cmd)

    test_filename = "test_user_permissions"  # must be unique among tests in this suite

    # alice has read/write access to her directory
    sdk_auth.kdestroy(kerberized_hdfs_client)
    sdk_auth.kinit(kerberized_hdfs_client, keytab=config.KEYTAB, principal=kerberos.get_principal("alice"))
    write_access_cmd = "/bin/bash -c \"{}\"".format(config.hdfs_write_command(
        config.TEST_CONTENT_SMALL,
        "/users/alice/{}".format(test_filename)))
    log.info("Alice can write: {}".format(write_access_cmd))
    rc, stdout, _ = sdk_cmd.task_exec(kerberized_hdfs_client, write_access_cmd)
    assert stdout == '' and rc == 0

    read_access_cmd = config.hdfs_read_command("/users/alice/{}".format(test_filename))
    log.info("Alice can read: {}".format(read_access_cmd))
    _, stdout, _ = sdk_cmd.task_exec(kerberized_hdfs_client, read_access_cmd)
    assert stdout == config.TEST_CONTENT_SMALL

    ls_cmd = config.hdfs_command("ls /users/alice")
    _, stdout, _ = sdk_cmd.task_exec(kerberized_hdfs_client, ls_cmd)
    assert "/users/alice/{}".format(test_filename) in stdout

    # bob doesn't have read/write access to alice's directory
    sdk_auth.kdestroy(kerberized_hdfs_client)
    sdk_auth.kinit(kerberized_hdfs_client, keytab=config.KEYTAB, principal=kerberos.get_principal("bob"))

    log.info("Bob tries to wrtie to alice's directory: {}".format(write_access_cmd))
    _, _, stderr = sdk_cmd.task_exec(kerberized_hdfs_client, write_access_cmd)
    log.info("Bob can't write to alice's directory: {}".format(write_access_cmd))
    assert "put: Permission denied: user=bob" in stderr

    log.info("Bob tries to read from alice's directory: {}".format(read_access_cmd))
    _, _, stderr = sdk_cmd.task_exec(kerberized_hdfs_client, read_access_cmd)
    log.info("Bob can't read from alice's directory: {}".format(read_access_cmd))
    assert "cat: Permission denied: user=bob" in stderr
