import pytest
import random
import string

import sdk_cmd
import sdk_tasks
import sdk_install
import sdk_security
import sdk_utils
import subprocess
from tests import config

PASSWORD_FILE = "/test/integration/cassandra/passwordfile"
ACCESS_FILE = "/test/integration/cassandra/access"
KEY_STORE = "/test/integration/cassandra/keystore"
KEY_STORE_PASS = "/test/integration/cassandra/keypass"
TRUST_STORE = "/test/integration/cassandra/keystore"
TRUST_STORE_PASS = "/test/integration/cassandra/keypass"


def install_jmx_configured_cassandra(self_signed_trust_store: bool = True):
    foldered_name = config.get_foldered_service_name()
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

    sdk_install.install(
        config.PACKAGE_NAME,
        foldered_name,
        config.DEFAULT_TASK_COUNT,
        additional_options=service_options,
    )


def install_jmx_secrets():
    test_run = random_string()
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
        "/tmp/{}-self-signed-keystore.ks".format(test_run),
        "-storepass",
        "deleteme",
        "-keypass",
        "deleteme",
        "-storetype",
        "jks",
    ]

    subprocess.check_output(create_keystore_cmd)

    create_keystore_cmd = [
        "keytool",
        "-list",
        "-v",
        "-keystore",
        "/tmp/{}-self-signed-keystore.ks".format(test_run),
        "-storepass",
        "deleteme",
    ]

    subprocess.check_output(create_keystore_cmd)

    write_to_file("deleteme", "/tmp/{}-keystorepass".format(test_run))
    write_to_file("admin adminpassword", "/tmp/{}-passwordfile".format(test_run))
    write_to_file("admin readwrite", "/tmp/{}-access".format(test_run))

    sdk_security.install_enterprise_cli(False)

    sdk_cmd.run_cli(
        "security secrets create -f /tmp/{}-self-signed-keystore.ks {}".format(test_run, KEY_STORE)
    )
    sdk_cmd.run_cli(
        "security secrets create -f /tmp/{}-passwordfile {}".format(test_run, PASSWORD_FILE)
    )
    sdk_cmd.run_cli(
        "security secrets create -f /tmp/{}-keystorepass {}".format(test_run, KEY_STORE_PASS)
    )
    sdk_cmd.run_cli("security secrets create -f /tmp/{}-access {}".format(test_run, ACCESS_FILE))


def uninstall_jmx_secrets():
    sdk_security.delete_secret(KEY_STORE)
    sdk_security.delete_secret(KEY_STORE_PASS)
    sdk_security.delete_secret(ACCESS_FILE)
    sdk_security.delete_secret(PASSWORD_FILE)


@pytest.mark.sanity
@sdk_utils.dcos_ee_only
@pytest.mark.parametrize("self_signed_trust_store", [True, False])
def test_secure_jmx_configuration(self_signed_trust_store):
    foldered_name = config.get_foldered_service_name()

    try:
        install_jmx_configured_cassandra(self_signed_trust_store=self_signed_trust_store)
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
            "-Djdk.tls.client.protocols=TLSv1.2 -Djavax.net.ssl.trustStore={trust_store} "
            "-Djavax.net.ssl.trustStorePassword={trust_store_password} "
            "-Djavax.net.ssl.keyStore=$MESOS_SANDBOX/jmx/key_store -Djavax.net.ssl.keyStorePassword=deleteme "
            "-Djavax.net.ssl.trustStoreType=JKS -Djavax.net.ssl.keyStoreType=JKS -jar jmxterm-1.0.1-uber.jar "
            "-l service:jmx:rmi:///jndi/rmi://$MESOS_CONTAINER_IP:7199/jmxrmi -u admin -p adminpassword "
            "-s -v silent -n".format(
                trust_store=trust_store, trust_store_password=trust_store_password
            )
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

    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
        uninstall_jmx_secrets()


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