import os
import uuid
import pytest
import random
import string
from typing import Any, Dict, List
import sdk_cmd
import sdk_tasks
import sdk_install
import sdk_security
import sdk_utils
import sdk_jobs
import subprocess
from tests import config
from tests import test_sanity
from tests import test_backup_and_restore

PASSWORD_FILE = "/test/integration/cassandra/passwordfile"
ACCESS_FILE = "/test/integration/cassandra/access"
KEY_STORE = "/test/integration/cassandra/keystore"
KEY_STORE_PASS = "/test/integration/cassandra/keypass"
TRUST_STORE = "/test/integration/cassandra/keystore"
TRUST_STORE_PASS = "/test/integration/cassandra/keypass"


def install_jmx_configured_cassandra(
    self_signed_trust_store: bool = True, authentication: bool = True
):
    foldered_name = config.get_foldered_service_name()
    test_jobs: List[Dict[str, Any]] = []

    if authentication:
        test_jobs = config.get_all_jobs(node_address=config.get_foldered_node_address(), auth=True)
    else:
        test_jobs = config.get_all_jobs(node_address=config.get_foldered_node_address())
    # destroy/reinstall any prior leftover jobs, so that they don't touch the newly installed service:
    for job in test_jobs:
        sdk_jobs.install_job(job)

    sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
    install_jmx_secrets()
    service_options = {
        "service": {
            "name": foldered_name,
            "jmx": {
                "enabled": True,
                "rmi_port": 31198,
                "password_file": PASSWORD_FILE,
                "access_file": ACCESS_FILE,
                "key_store": KEY_STORE,
                "key_store_password_file": KEY_STORE_PASS,
            },
        }
    }

    if self_signed_trust_store:
        service_options = sdk_utils.merge_dictionaries(
            {
                "service": {
                    "jmx": {
                        "add_trust_store": True,
                        "trust_store": TRUST_STORE,
                        "trust_store_password_file": TRUST_STORE_PASS,
                    }
                }
            },
            service_options,
        )

    if authentication:
        secret_path = foldered_name + "/" + config.SECRET_VALUE
        create_secret(secret_value=config.SECRET_VALUE, secret_path=secret_path)
        service_options = sdk_utils.merge_dictionaries(
            {
                "service": {
                    "security": {
                        "authentication": {
                            "enabled": True,
                            "superuser": {"password_secret_path": secret_path},
                        },
                        "authorization": {"enabled": True},
                    }
                }
            },
            service_options,
        )

    sdk_install.install(
        config.PACKAGE_NAME,
        foldered_name,
        config.DEFAULT_TASK_COUNT,
        additional_options=service_options,
    )


def install_jmx_secrets():
    uninstall_jmx_secrets()
    test_run_id = random_string()
    create_keystore_cmd = [
        "keytool",
        "-genkey",
        "-alias",
        "self-signed-cert",
        "-keyalg",
        "rsa",
        "-dname",
        "CN=myhost.example.com,O=Example Company,C=US",
        "-keystore",
        "/tmp/{}-self-signed-keystore.ks".format(test_run_id),
        "-storepass",
        "deleteme",
        "-keypass",
        "deleteme",
        "-storetype",
        "jks",
    ]

    subprocess.check_output(create_keystore_cmd)

    keystore_list_cmd = [
        "keytool",
        "-list",
        "-v",
        "-keystore",
        "/tmp/{}-self-signed-keystore.ks".format(test_run_id),
        "-storepass",
        "deleteme",
    ]

    subprocess.check_output(keystore_list_cmd)

    write_to_file("deleteme", "/tmp/{}-keystorepass".format(test_run_id))
    write_to_file("admin adminpassword", "/tmp/{}-passwordfile".format(test_run_id))
    write_to_file("admin readwrite", "/tmp/{}-access".format(test_run_id))

    sdk_security.install_enterprise_cli(False)

    sdk_cmd.run_cli(
        "security secrets create -f /tmp/{}-self-signed-keystore.ks {}".format(
            test_run_id, KEY_STORE
        )
    )
    sdk_cmd.run_cli(
        "security secrets create -f /tmp/{}-passwordfile {}".format(test_run_id, PASSWORD_FILE)
    )
    sdk_cmd.run_cli(
        "security secrets create -f /tmp/{}-keystorepass {}".format(test_run_id, KEY_STORE_PASS)
    )
    sdk_cmd.run_cli("security secrets create -f /tmp/{}-access {}".format(test_run_id, ACCESS_FILE))


def uninstall_jmx_secrets():
    sdk_security.delete_secret(KEY_STORE)
    sdk_security.delete_secret(KEY_STORE_PASS)
    sdk_security.delete_secret(ACCESS_FILE)
    sdk_security.delete_secret(PASSWORD_FILE)


@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def check_secure_jmx_output(self_signed_trust_store, authentication):
    foldered_name = config.get_foldered_service_name()

    node_task_id_0 = sdk_tasks.get_task_ids(foldered_name)[0]
    install_jmxterm(task_id=node_task_id_0)
    generate_jmx_command_files(task_id=node_task_id_0)

    if self_signed_trust_store:
        trust_store = "$MESOS_SANDBOX/jmx/trust_store"
        trust_store_password = "deleteme"
    else:
        trust_store = "$JAVA_HOME/lib/security/cacerts"
        trust_store_password = "changeit"

    cmd = (
        "export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jdk*/) && "
        "$JAVA_HOME/bin/java "
        "-Duser.home=$MESOS_SANDBOX "
        "-Djdk.tls.client.protocols=TLSv1.2 "
        "-Djavax.net.ssl.trustStore={trust_store} "
        "-Djavax.net.ssl.trustStorePassword={trust_store_password} "
        "-Djavax.net.ssl.keyStore=$MESOS_SANDBOX/jmx/key_store -Djavax.net.ssl.keyStorePassword=deleteme "
        "-Djavax.net.ssl.trustStoreType=JKS -Djavax.net.ssl.keyStoreType=JKS -jar jmxterm-1.0.1-uber.jar "
        "-l service:jmx:rmi:///jndi/rmi://$MESOS_CONTAINER_IP:7199/jmxrmi -u admin -p adminpassword "
        "-s -v silent -n".format(trust_store=trust_store, trust_store_password=trust_store_password)
    )

    input_jmx_commands = " < jmx_beans_command.txt"

    full_cmd = "bash -c '{}{}'".format(cmd, input_jmx_commands)

    _, output, _ = sdk_cmd.run_cli(
        "task exec {} {}".format(node_task_id_0, full_cmd), print_output=True
    )

    assert "org.apache.cassandra.net:type=FailureDetector" in output
    assert "org.apache.cassandra.net:type=Gossiper" in output

    input_jmx_commands = " < jmx_domains_command.txt"
    full_cmd = "bash -c '{}{}'".format(cmd, input_jmx_commands)
    rc, output, stderr = sdk_cmd.run_cli(
        "task exec {} {}".format(node_task_id_0, full_cmd), print_output=True
    )

    assert "org.apache.cassandra.metrics" in output
    assert "org.apache.cassandra.service" in output


@pytest.mark.sanity
@sdk_utils.dcos_ee_only
@pytest.mark.parametrize("self_signed_trust_store", [True])
def test_secure_jmx_cmd_without_auth(self_signed_trust_store):
    install_jmx_configured_cassandra(
        self_signed_trust_store=self_signed_trust_store, authentication=False
    )
    check_secure_jmx_output(self_signed_trust_store=self_signed_trust_store, authentication=False)


@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_repair_cleanup_plans_with_jmx():
    test_sanity.test_repair_cleanup_plans_complete()


@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_backup_and_restore_to_s3_with_jmx_without_auth():
    test_backup_and_restore.test_backup_and_restore_to_s3()


@pytest.mark.sanity
@sdk_utils.dcos_ee_only
@pytest.mark.parametrize("self_signed_trust_store", [False, True])
def test_secure_jmx_cmd_with_auth(self_signed_trust_store):
    install_jmx_configured_cassandra(
        self_signed_trust_store=self_signed_trust_store, authentication=True
    )
    check_secure_jmx_output(self_signed_trust_store=self_signed_trust_store, authentication=True)


@pytest.mark.sanity
@sdk_utils.dcos_ee_only
def test_backup_and_restore_to_s3_with_jmx_with_auth():
    key_id = os.getenv("AWS_ACCESS_KEY_ID")
    if not key_id:
        assert (
            False
        ), 'AWS credentials are required for this test. Disable test with e.g. TEST_TYPES="sanity and not aws"'
    plan_parameters = {
        "AWS_ACCESS_KEY_ID": key_id,
        "AWS_SECRET_ACCESS_KEY": os.getenv("AWS_SECRET_ACCESS_KEY"),
        "AWS_REGION": os.getenv("AWS_REGION", "us-west-2"),
        "S3_BUCKET_NAME": os.getenv("AWS_BUCKET_NAME", "infinity-framework-test"),
        "SNAPSHOT_NAME": str(uuid.uuid1()),
        "CASSANDRA_KEYSPACES": '"testspace1 testspace2"',
    }

    config.run_backup_and_restore_with_auth(
        config.get_foldered_service_name(),
        "backup-s3",
        "restore-s3",
        plan_parameters,
        config.get_foldered_node_address(),
    )
    sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
    test_jobs: List[Dict[str, Any]] = []
    test_jobs = config.get_all_jobs(node_address=config.get_foldered_node_address(), auth=True)
    for job in test_jobs:
        sdk_jobs.remove_job(job)


def random_string(length=10):
    letters = string.ascii_lowercase
    return "".join(random.choice(letters) for i in range(length))


def write_to_file(content, file_path):
    text_file = open(file_path, "w+")
    text_file.write(content)
    text_file.close()


def generate_jmx_command_files(task_id: string):
    cmd = "\n".join(
        ["echo beans >> jmx_beans_command.txt && ", "echo domains >> jmx_domains_command.txt"]
    )
    full_cmd = "bash -c '{}'".format(cmd)
    rc, stdout, stderr = sdk_cmd.run_cli(
        "task exec {} {}".format(task_id, full_cmd), print_output=True
    )
    assert rc == 0, "Error creating jmx_commands file"


def install_jmxterm(task_id: string):
    jmx_term_url = "https://downloads.mesosphere.io/jmx/assets/jmxterm-1.0.1-uber.jar"
    cmd = "/opt/mesosphere/bin/curl {} --output jmxterm-1.0.1-uber.jar".format(jmx_term_url)
    full_cmd = "bash -c '{}'".format(cmd)
    rc, stdout, stderr = sdk_cmd.run_cli(
        "task exec {} {}".format(task_id, full_cmd), print_output=True
    )
    assert rc == 0, "Error downloading jmxterm {}".format(jmx_term_url)


def create_secret(secret_value: str, secret_path: str) -> None:
    sdk_security.delete_secret(secret_path)
    sdk_cmd.run_cli(
        'security secrets create --value="{account}" "{secret}"'.format(
            account=secret_value, secret=secret_path
        )
    )
