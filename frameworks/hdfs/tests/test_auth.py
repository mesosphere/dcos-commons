import base64
import logging
import pytest
import itertools

import sdk_auth
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_tasks
import sdk_utils
from tests import config


log = logging.getLogger(__name__)


def get_principals() -> list:
    """
    Sets up the appropriate principals needed for a kerberized deployment of HDFS.
    :return: A list of said principals
    """
    primaries = ["hdfs", "HTTP"]
    fqdn = "{service_name}.{host_suffix}".format(
        service_name=config.FOLDERED_DNS_NAME, host_suffix=sdk_hosts.AUTOIP_HOST_SUFFIX)
    instances = [
        "name-0-node",
        "name-0-zkfc",
        "name-1-node",
        "name-1-zkfc",
        "journal-0-node",
        "journal-1-node",
        "journal-2-node",
        "data-0-node",
        "data-1-node",
        "data-2-node",
    ]
    principals = []
    for (primary, instance) in itertools.product(primaries, instances):
        principals.append(
            "{primary}/{instance}.{fqdn}@{REALM}".format(
                primary=primary,
                instance=instance,
                fqdn=fqdn,
                REALM=sdk_auth.REALM
            )
        )
    principals.extend(config.CLIENT_PRINCIPALS.values())

    http_principal = "HTTP/api.{}.marathon.l4lb.thisdcos.directory".format(config.FOLDERED_DNS_NAME)
    principals.append(http_principal)
    return principals


def get_principal_to_user_mapping() -> str:
    """
    Kerberized HDFS maps the primary component of a principal to local users, so
    we need to create an appropriate mapping to test authorization functionality.
    :return: A base64-encoded string of principal->user mappings
    """
    rules = [
        "RULE:[2:$1@$0](^hdfs@.*$)s/.*/hdfs/",
        "RULE:[1:$1@$0](^nobody@.*$)s/.*/nobody/"
    ]

    for user in config.CLIENT_PRINCIPALS.keys():
        rules.append("RULE:[1:$1@$0](^{user}@.*$)s/.*/{user}/".format(user=user))

    return base64.b64encode('\n'.join(rules).encode("utf-8")).decode("utf-8")


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security):
    try:
        principals = get_principals()
        kerberos_env = sdk_auth.KerberosEnvironment()
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
                        "realm": sdk_auth.REALM
                    }
                }
            },
            "hdfs": {
                "security_auth_to_local": get_principal_to_user_mapping()
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
    sdk_auth.kinit(kerberized_hdfs_client, keytab=config.KEYTAB, principal=config.CLIENT_PRINCIPALS["hdfs"])

    write_cmd = "/bin/bash -c '{}'".format(config.hdfs_write_command(config.TEST_CONTENT_SMALL, config.TEST_FILE_1_NAME))
    sdk_tasks.task_exec(kerberized_hdfs_client, write_cmd)

    read_cmd = "/bin/bash -c '{}'".format(config.hdfs_read_command(config.TEST_FILE_1_NAME))
    _, stdout, _ = sdk_tasks.task_exec(kerberized_hdfs_client, read_cmd)
    assert stdout == config.TEST_CONTENT_SMALL


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.auth
@pytest.mark.sanity
def test_users_have_appropriate_permissions(kerberized_hdfs_client):
    # "hdfs" is a superuser
    sdk_auth.kinit(kerberized_hdfs_client, keytab=config.KEYTAB, principal=config.CLIENT_PRINCIPALS["hdfs"])

    log.info("Creating directory for alice")
    make_user_directory_cmd = config.hdfs_command("mkdir -p /users/alice")
    sdk_tasks.task_exec(kerberized_hdfs_client, make_user_directory_cmd)

    change_ownership_cmd = config.hdfs_command("chown alice:users /users/alice")
    sdk_tasks.task_exec(kerberized_hdfs_client, change_ownership_cmd)

    change_permissions_cmd = config.hdfs_command("chmod 700 /users/alice")
    sdk_tasks.task_exec(kerberized_hdfs_client, change_permissions_cmd)

    # alice has read/write access to her directory
    sdk_auth.kdestroy(kerberized_hdfs_client)
    sdk_auth.kinit(kerberized_hdfs_client, keytab=config.KEYTAB, principal=config.CLIENT_PRINCIPALS["alice"])
    write_access_cmd = "/bin/bash -c \"{}\"".format(config.hdfs_write_command(
        config.TEST_CONTENT_SMALL,
        "/users/alice/{}".format(config.TEST_FILE_1_NAME)))
    log.info("Alice can write: {}".format(write_access_cmd))
    rc, stdout, _ = sdk_tasks.task_exec(kerberized_hdfs_client, write_access_cmd)
    assert stdout == '' and rc == 0

    read_access_cmd = config.hdfs_read_command("/users/alice/{}".format(config.TEST_FILE_1_NAME))
    log.info("Alice can read: {}".format(read_access_cmd))
    _, stdout, _ = sdk_tasks.task_exec(kerberized_hdfs_client, read_access_cmd)
    assert stdout == config.TEST_CONTENT_SMALL

    ls_cmd = config.hdfs_command("ls /users/alice")
    _, stdout, _ = sdk_tasks.task_exec(kerberized_hdfs_client, ls_cmd)
    assert "/users/alice/{}".format(config.TEST_FILE_1_NAME) in stdout

    # bob doesn't have read/write access to alice's directory
    sdk_auth.kdestroy(kerberized_hdfs_client)
    sdk_auth.kinit(kerberized_hdfs_client, keytab=config.KEYTAB, principal=config.CLIENT_PRINCIPALS["bob"])

    log.info("Bob tries to wrtie to alice's directory: {}".format(write_access_cmd))
    _, _, stderr = sdk_tasks.task_exec(kerberized_hdfs_client, write_access_cmd)
    log.info("Bob can't write to alice's directory: {}".format(write_access_cmd))
    assert "put: Permission denied: user=bob" in stderr

    log.info("Bob tries to read from alice's directory: {}".format(read_access_cmd))
    _, _, stderr = sdk_tasks.task_exec(kerberized_hdfs_client, read_access_cmd)
    log.info("Bob can't read from alice's directory: {}".format(read_access_cmd))
    assert "cat: Permission denied: user=bob" in stderr
