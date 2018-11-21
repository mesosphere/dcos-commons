import logging
import pytest

import sdk_auth
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_tasks
import sdk_utils

from security import kerberos as krb5
from security import transport_encryption

from tests import auth
from tests import config


log = logging.getLogger(__name__)
foldered_name = config.FOLDERED_SERVICE_NAME


pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"),
        reason="Kerberos tests require DC/OS 1.10 or higher",
    ),
]


@pytest.fixture(scope="module", autouse=True)
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        service_account_info = transport_encryption.setup_service_account(foldered_name)
        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(foldered_name, service_account_info)


@pytest.fixture(scope="module", autouse=True)
def kerberos(configure_security):
    try:
        principals = auth.get_service_principals(foldered_name, sdk_auth.REALM)

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
            "name": foldered_name,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {
                "kerberos": {
                    "enabled": True,
                    "debug": True,
                    "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            },
        },
        "hdfs": {"security_auth_to_local": auth.get_principal_to_user_mapping()},
    }

    sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            foldered_name,
            config.DEFAULT_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
        )

        yield {**service_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)


@pytest.fixture(scope="module", autouse=True)
def hdfs_client(hdfs_server, kerberos):
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

    test_filename = config.get_unique_filename("test_kerberos_auth_write_read")
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
    success = True
    cmd_lists = [
        ["mkdir", "-p", alice_dir],
        ["chown", "alice:users", alice_dir],
        ["chmod", "700", alice_dir],
    ]
    for cmd_list in cmd_lists:
        cmd = config.hdfs_command(" ".join(cmd_list))
        cmd_success = config.run_client_command(cmd)
        if not cmd_success:
            log.error("Error executing: %s", cmd)
        success = success and cmd_success

    if not success:
        log.error("Error creating %s", alice_dir)
        raise Exception("Error creating user directory")

    # alice has read/write access to her directory
    sdk_auth.kdestroy(hdfs_client["id"])
    sdk_auth.kinit(
        hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("alice")
    )

    test_filename = "{}/{}".format(alice_dir, config.get_unique_filename("test_kerberos_auth_user_permissions"))
    config.hdfs_client_write_data(test_filename)
    config.hdfs_client_read_data(test_filename)
    _, stdout, _ = config.hdfs_client_list_files(alice_dir)
    assert test_filename in stdout

    # bob doesn't have read/write access to alice's directory
    sdk_auth.kdestroy(hdfs_client["id"])
    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("bob"))

    config.hdfs_client_write_data(test_filename, expect_failure_message="put: Permission denied: user=bob")
    config.hdfs_client_read_data(test_filename, expect_failure_message="cat: Permission denied: user=bob")


@pytest.mark.auth
@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_journalnodes(hdfs_server):
    service_name = hdfs_server["service"]["name"]
    journal_ids = sdk_tasks.get_task_ids(service_name, "journal")
    name_ids = sdk_tasks.get_task_ids(service_name, "name")
    data_ids = sdk_tasks.get_task_ids(service_name, "data")

    for journal_pod in config.get_pod_type_instances("journal", service_name):
        sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, "pod restart {}".format(journal_pod))
        config.expect_recovery(service_name=service_name)

    sdk_tasks.check_tasks_updated(service_name, "journal", journal_ids)
    sdk_tasks.check_tasks_not_updated(service_name, "name", name_ids)
    sdk_tasks.check_tasks_not_updated(service_name, "data", data_ids)
