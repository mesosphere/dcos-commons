import base64
import os
import logging
import pytest

import sdk_auth
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_utils

from tests import config


ACTIVE_DIRECTORY_ENVVAR = 'TESTING_ACTIVE_DIRECTORY_SERVER'


def is_active_directory_enabled():
    return ACTIVE_DIRECTORY_ENVVAR in os.environ


pytestmark = pytest.mark.skipif(not is_active_directory_enabled(),
                                reason="This test requires TESTING_ACTIVE_DIRECTORY_SERVER to be set")


log = logging.getLogger(__name__)


USERS = [
    "hdfs",
    "alice",
    "bob",
]


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

    for user in USERS:
        rules.append("RULE:[1:$1@$0](^{user}@.*$)s/.*/{user}/".format(user=user))

    return base64.b64encode('\n'.join(rules).encode("utf-8")).decode("utf-8")


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


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_security):
    try:
        kerberos_env = ActiveDirectoryKerberos(config.SERVICE_NAME)
        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.fixture(scope='module', autouse=True)
def hdfs_server(kerberos):
    """
    A pytest fixture that installs a Kerberized HDFS service.

    On teardown, the service is uninstalled.
    """
    service_kerberos_options = {
        "service": {
            "name": config.SERVICE_NAME,
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
        },
        "hdfs": {
            "security_auth_to_local": get_principal_to_user_mapping()
        }
    }

    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    try:
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=service_kerberos_options,
            timeout_seconds=30 * 60)

        yield {**service_kerberos_options, **{"package_name": config.PACKAGE_NAME}}
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


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
                    "image": "nvaziri/hdfs-client:dev",
                    "forcePullImage": True
                },
                "volumes": [
                    {
                        "containerPath": "/hadoop-2.6.0-cdh5.9.1/hdfs.keytab",
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
                "HDFS_SERVICE_NAME": config.SERVICE_NAME,
            }
        }

        sdk_marathon.install_app(client)

        write_krb5_config_file(client_id, "/etc/krb5.conf", kerberos)

        yield client

    finally:
        sdk_marathon.destroy_app(client_id)


def write_krb5_config_file(task: str, filename: str, krb5: object) -> str:
    """
    Generate a Kerberos config file.
    TODO(elezar): This duplicates functionality in frameworks/kafka/tests/auth.py
    """
    output_file = filename

    log.info("Generating %s", output_file)
    krb5_file_contents = ['[libdefaults]',
                          'default_realm = {}'.format(krb5.get_realm()),
                          '',
                          '[realms]',
                          '  {realm} = {{'.format(realm=krb5.get_realm()),
                          '    kdc = {}'.format(krb5.get_kdc_address()),
                          '  }', ]
    log.info("%s", krb5_file_contents)

    output = sdk_cmd.create_task_text_file(task, output_file, krb5_file_contents)
    log.info(output)

    return output_file


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.auth
@pytest.mark.sanity
def test_user_can_auth_and_write_and_read(hdfs_client, kerberos):
    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("hdfs"))

    test_filename = "test_auth_write_read"  # must be unique among tests in this suite
    write_cmd = "/bin/bash -c '{}'".format(config.hdfs_write_command(config.TEST_CONTENT_SMALL, test_filename))
    sdk_cmd.task_exec(hdfs_client["id"], write_cmd)

    read_cmd = "/bin/bash -c '{}'".format(config.hdfs_read_command(test_filename))
    _, stdout, _ = sdk_cmd.task_exec(hdfs_client["id"], read_cmd)
    assert stdout == config.TEST_CONTENT_SMALL


@pytest.mark.dcos_min_version('1.10')
@sdk_utils.dcos_ee_only
@pytest.mark.auth
@pytest.mark.sanity
def test_users_have_appropriate_permissions(hdfs_client, kerberos):
    # "hdfs" is a superuser

    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("hdfs"))

    log.info("Creating directory for alice")
    make_user_directory_cmd = config.hdfs_command("mkdir -p /users/alice")
    sdk_cmd.task_exec(hdfs_client["id"], make_user_directory_cmd)

    change_ownership_cmd = config.hdfs_command("chown alice:users /users/alice")
    sdk_cmd.task_exec(hdfs_client["id"], change_ownership_cmd)

    change_permissions_cmd = config.hdfs_command("chmod 700 /users/alice")
    sdk_cmd.task_exec(hdfs_client["id"], change_permissions_cmd)

    test_filename = "test_user_permissions"  # must be unique among tests in this suite

    # alice has read/write access to her directory
    sdk_auth.kdestroy(hdfs_client["id"])
    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("alice"))
    write_access_cmd = "/bin/bash -c \"{}\"".format(config.hdfs_write_command(
        config.TEST_CONTENT_SMALL,
        "/users/alice/{}".format(test_filename)))
    log.info("Alice can write: %s", write_access_cmd)
    rc, stdout, _ = sdk_cmd.task_exec(hdfs_client["id"], write_access_cmd)
    assert stdout == '' and rc == 0

    read_access_cmd = config.hdfs_read_command("/users/alice/{}".format(test_filename))
    log.info("Alice can read: %s", read_access_cmd)
    _, stdout, _ = sdk_cmd.task_exec(hdfs_client["id"], read_access_cmd)
    assert stdout == config.TEST_CONTENT_SMALL

    ls_cmd = config.hdfs_command("ls /users/alice")
    _, stdout, _ = sdk_cmd.task_exec(hdfs_client["id"], ls_cmd)
    assert "/users/alice/{}".format(test_filename) in stdout

    # bob doesn't have read/write access to alice's directory
    sdk_auth.kdestroy(hdfs_client["id"])
    sdk_auth.kinit(hdfs_client["id"], keytab=config.KEYTAB, principal=kerberos.get_principal("bob"))

    log.info("Bob tries to wrtie to alice's directory: %s", write_access_cmd)
    _, _, stderr = sdk_cmd.task_exec(hdfs_client["id"], write_access_cmd)
    log.info("Bob can't write to alice's directory: %s", write_access_cmd)
    assert "put: Permission denied: user=bob" in stderr

    log.info("Bob tries to read from alice's directory: %s", read_access_cmd)
    _, _, stderr = sdk_cmd.task_exec(hdfs_client["id"], read_access_cmd)
    log.info("Bob can't read from alice's directory: %s", read_access_cmd)
    assert "cat: Permission denied: user=bob" in stderr
