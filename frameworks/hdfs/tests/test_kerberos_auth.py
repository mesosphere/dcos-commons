import logging
import uuid
import pytest

import sdk_auth
import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_marathon
import sdk_tasks
import sdk_utils

from security import kerberos as krb5
from security import transport_encryption

from tests import auth
from tests import config


log = logging.getLogger(__name__)


pytestmark = [pytest.mark.skipif(sdk_utils.is_open_dcos(),
                                 reason="Feature only supported in DC/OS EE"),
              pytest.mark.skipif(sdk_utils.dcos_version_less_than("1.10"),
                                 reason="Kerberos tests require DC/OS 1.10 or higher")]


@pytest.fixture(scope='module', autouse=True)
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        service_account_info = transport_encryption.setup_service_account(config.FOLDERED_SERVICE_NAME)
        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(config.FOLDERED_SERVICE_NAME, service_account_info)


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security):
    try:
        principals = auth.get_service_principals(config.FOLDERED_SERVICE_NAME, sdk_auth.REALM)

        kerberos_env = sdk_auth.KerberosEnvironment()
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module', autouse=True)
def hdfs_server(kerberos, service_account):
    """
    A pytest fixture that installs a Kerberized HDFS service.

    On teardown, the service is uninstalled.
    """
    service_options = {
        "service": {
            "name": config.FOLDERED_SERVICE_NAME,
            "service_account": service_account["name"],
            "service_account_secret": service_account["secret"],
            "security": {
                "kerberos": {
                    "enabled": True,
                    "debug": True,
                    "kdc": {
                        "hostname": kerberos.get_host(),
                        "port": int(kerberos.get_port())
                    },
                    "realm": kerberos.get_realm(),
                    "keytab_secret": kerberos.get_keytab_path(),
                }
            }
        },
        "hdfs": {
            "security_auth_to_local": auth.get_principal_to_user_mapping()
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.FOLDERED_SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.FOLDERED_SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60)

        yield {**service_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.FOLDERED_SERVICE_NAME)


@pytest.fixture(scope='module', autouse=True)
def hdfs_client(kerberos, hdfs_server):
    try:
        client_id = "hdfs-client"
        client = {
            "id": client_id,
            "mem": 1024,
            "user": "nobody",
            "container": {
                "type": "MESOS",
                "docker": {
                    "image": "nvaziri/hdfs-client:stable",
                    "forcePullImage": True
                },
                "volumes": [
                    {
                        "containerPath": "/{}/hdfs.keytab".format(config.HADOOP_VERSION),
                        "secret": "hdfs_keytab"
                    }
                ]
            },
            "secrets": {
                "hdfs_keytab": {
                    "source": kerberos.get_keytab_path()
                }
            },
            "networks": [
                {
                    "mode": "host"
                }
            ],
            "env": {
                "REALM": kerberos.get_realm(),
                "KDC_ADDRESS": kerberos.get_kdc_address(),
                "JAVA_HOME": "/usr/lib/jvm/default-java",
                "KRB5_CONFIG": "/etc/krb5.conf",
                "HDFS_SERVICE_NAME": sdk_hosts._safe_name(config.FOLDERED_SERVICE_NAME),
                "HADOOP_VERSION": config.HADOOP_VERSION
            }
        }

        sdk_marathon.install_app(client)

        krb5.write_krb5_config_file(client_id, "/etc/krb5.conf", kerberos)

        yield client

    finally:
        sdk_marathon.destroy_app(client_id)


@pytest.mark.auth
@pytest.mark.sanity
def test_user_can_auth_and_write_and_read(hdfs_client, kerberos):
    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("hdfs"))

    test_filename = "test_auth_write_read-{}".format(str(uuid.uuid4()))
    write_cmd = "/bin/bash -c '{}'".format(config.hdfs_write_command(config.TEST_CONTENT_SMALL, test_filename))
    sdk_cmd.marathon_task_exec(hdfs_client["id"], write_cmd)

    read_cmd = "/bin/bash -c '{}'".format(config.hdfs_read_command(test_filename))
    _, stdout, _ = sdk_cmd.marathon_task_exec(hdfs_client["id"], read_cmd)
    assert stdout == config.TEST_CONTENT_SMALL


@pytest.mark.auth
@pytest.mark.sanity
def test_users_have_appropriate_permissions(hdfs_client, kerberos):
    # "hdfs" is a superuser

    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("hdfs"))

    log.info("Creating directory for alice")
    make_user_directory_cmd = config.hdfs_command("mkdir -p /users/alice")
    sdk_cmd.marathon_task_exec(hdfs_client["id"], make_user_directory_cmd)

    change_ownership_cmd = config.hdfs_command("chown alice:users /users/alice")
    sdk_cmd.marathon_task_exec(hdfs_client["id"], change_ownership_cmd)

    change_permissions_cmd = config.hdfs_command("chmod 700 /users/alice")
    sdk_cmd.marathon_task_exec(hdfs_client["id"], change_permissions_cmd)

    test_filename = "test_user_permissions-{}".format(str(uuid.uuid4()))

    # alice has read/write access to her directory
    sdk_auth.kdestroy(hdfs_client["id"])
    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("alice"))
    write_access_cmd = "/bin/bash -c '{}'".format(config.hdfs_write_command(
        config.TEST_CONTENT_SMALL,
        "/users/alice/{}".format(test_filename)))
    log.info("Alice can write: %s", write_access_cmd)
    rc, stdout, _ = sdk_cmd.marathon_task_exec(hdfs_client["id"], write_access_cmd)
    assert stdout == '' and rc == 0

    read_access_cmd = "/bin/bash -c '{}'".format(config.hdfs_read_command("/users/alice/{}".format(test_filename)))
    log.info("Alice can read: %s", read_access_cmd)
    _, stdout, _ = sdk_cmd.marathon_task_exec(hdfs_client["id"], read_access_cmd)
    assert stdout == config.TEST_CONTENT_SMALL

    ls_cmd = config.hdfs_command("ls /users/alice")
    _, stdout, _ = sdk_cmd.marathon_task_exec(hdfs_client["id"], ls_cmd)
    assert "/users/alice/{}".format(test_filename) in stdout

    # bob doesn't have read/write access to alice's directory
    sdk_auth.kdestroy(hdfs_client["id"])
    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("bob"))

    log.info("Bob tries to wrtie to alice's directory: %s", write_access_cmd)
    _, _, stderr = sdk_cmd.marathon_task_exec(hdfs_client["id"], write_access_cmd)
    log.info("Bob can't write to alice's directory: %s", write_access_cmd)
    assert "put: Permission denied: user=bob" in stderr

    log.info("Bob tries to read from alice's directory: %s", read_access_cmd)
    _, _, stderr = sdk_cmd.marathon_task_exec(hdfs_client["id"], read_access_cmd)
    log.info("Bob can't read from alice's directory: %s", read_access_cmd)
    assert "cat: Permission denied: user=bob" in stderr


@pytest.mark.auth
@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_all_journalnodes(hdfs_server):
    service_name = hdfs_server["service"]["name"]
    journal_ids = sdk_tasks.get_task_ids(service_name, 'journal')
    name_ids = sdk_tasks.get_task_ids(service_name, 'name')
    data_ids = sdk_tasks.get_task_ids(service_name, 'data')

    for journal_pod in config.get_pod_type_instances("journal", service_name):
        sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'pod restart {}'.format(journal_pod))
        config.expect_recovery(service_name=service_name)

    sdk_tasks.check_tasks_updated(service_name, 'journal', journal_ids)
    sdk_tasks.check_tasks_not_updated(service_name, 'name', name_ids)
    sdk_tasks.check_tasks_not_updated(service_name, 'data', data_ids)



@pytest.mark.auth
@pytest.mark.sanity
@pytest.mark.recovery
def test_pod_restart_namenodes(hdfs_server):
    service_name = hdfs_server["service"]["name"]
    name_ids = sdk_tasks.get_task_ids(service_name, 'name')

    for name_pod in config.get_pod_type_instances("name", service_name):
        sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'pod restart {}'.format(name_pod))
        config.expect_recovery(service_name=service_name)

    sdk_tasks.check_tasks_not_updated(service_name, 'name', name_ids)


@pytest.mark.auth
@pytest.mark.sanity
@pytest.mark.recovery
def test_pod_replace_namenodes(hdfs_server):
    service_name = hdfs_server["service"]["name"]
    name_ids = sdk_tasks.get_task_ids(service_name, 'name')

    for name_pod in config.get_pod_type_instances("name", service_name):
        sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'pod replace {}'.format(name_pod))
        config.expect_recovery(service_name=service_name)

    sdk_tasks.check_tasks_not_updated(service_name, 'name', name_ids)
