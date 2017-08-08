import os
import time

import pytest
import shakedown
from cryptography import x509
from cryptography.hazmat.backends import default_backend
from cryptography.x509.oid import NameOID
from cryptography.x509.oid import ExtensionOID

import sdk_cmd
import sdk_install
import sdk_plan
import sdk_hosts
import sdk_marathon
import sdk_security
import sdk_tasks
import sdk_utils
import shakedown
from tests.config import (
    PACKAGE_NAME
)


DEFAULT_BACKEND = default_backend()

# Names of VIP aliases defined in examples/tls.yml
KEYSTORE_TASK_HTTPS_PORT_NAME = 'keystore-https'
NGINX_TASK_HTTPS_PORT_NAME = 'nginx-https'

# Default keystore passphrase which is hardcoded in dcos-commons implementation
KEYSTORE_PASS = "notsecure"

# Both these files are downloaded from single `keystore-app-{VERSION}.zip`
# artfiact. For more details see `testing/tls/keystore` directory in this
# project.
KEYSTORE_APP_JAR_NAME = 'keystore-app-0.1-SNAPSHOT-all.jar'
KEYSTORE_APP_CONFIG_NAME = 'integration-test.yml'

# Service discovery prefix for the `discovery` pod which allows testing
# udpates.
DISCOVERY_TASK_PREFIX = 'discovery-prefix'


@pytest.fixture(scope='module')
def dcos_security_cli():
    """
    Installs the dcos enterprise cli.
    """

    sdk_cmd.run_cli("package install --yes dcos-enterprise-cli")


@pytest.fixture(scope='module')
def service_account(dcos_security_cli):
    """
    Creates service account with `hello-world` name and yields the name.
    """
    name = 'hello-world'
    sdk_security.create_service_account(
        service_account_name=name, secret_name=name)
    # TODO(mh): Fine grained permissions needs to be addressed in DCOS-16475
    sdk_cmd.run_cli(
        "security org groups add_user superusers {name}".format(name=name))
    yield name
    sdk_security.delete_service_account(
        service_account_name=name, secret_name=name)


@pytest.fixture(scope='module')
def hello_world_service(service_account):
    sdk_install.install(
        PACKAGE_NAME,
        1,
        service_name=service_account,
        additional_options={
            "service": {
                "spec_file": "examples/tls.yml",
                "secret_name": service_account,
                "principal": service_account,
                },
            "tls": {
                "discovery_task_prefix": DISCOVERY_TASK_PREFIX,
                },
            }
        )

    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)

    # Wait for service health check to pass
    shakedown.service_healthy(PACKAGE_NAME)

    # TODO(mh): Add proper wait for health check
    time.sleep(15)

    yield service_account

    sdk_install.uninstall(PACKAGE_NAME)

    # Make sure that all the TLS artifacts were removed from the secrets store.
    output = sdk_cmd.run_cli('security secrets list {name}'.format(
        name=PACKAGE_NAME))
    artifact_suffixes = [
        'certificate', 'private-key', 'root-ca-certificate',
        'keystore', 'truststore'
        ]

    for suffix in artifact_suffixes:
        assert suffix not in output


@pytest.mark.tls
@pytest.mark.sanity
@sdk_utils.dcos_1_10_or_higher
def test_java_truststore(hello_world_service):
    """
    Make an HTTP request from CLI to nginx exposed service.
    Test that CLI reads and uses truststore to verify HTTPS connection.
    """
    task_id = sdk_tasks.get_task_ids(PACKAGE_NAME, "keystore")[0]
    assert task_id

    # Make an http request from a CLI app using configured keystore to the
    # service itself exposed via VIP.
    # This will test whether the service is serving correct end-entity
    # certificate from keystore and if CLI client can verify certificate
    # with custom truststore configuration.
    command = _java_command(
        'java -jar ' + KEYSTORE_APP_JAR_NAME + ' truststoretest '
        'integration-test.yml '
        'https://' + sdk_hosts.vip_host(
            PACKAGE_NAME, NGINX_TASK_HTTPS_PORT_NAME))
    output = task_exec(task_id, command)
    # Unfortunately the `dcos task exec` doesn't respect the return code
    # from executed command in container so we need to manually assert for
    # expected output.
    assert 'status=200' in output


@pytest.mark.tls
@pytest.mark.sanity
@sdk_utils.dcos_1_10_or_higher
def test_tls_basic_artifacts(hello_world_service):
    task_id = sdk_tasks.get_task_ids(PACKAGE_NAME, 'artifacts')[0]
    assert task_id

    # Load end-entity certificate from keystore and root CA cert from truststore
    end_entity_cert = x509.load_pem_x509_certificate(
        task_exec(task_id, 'cat secure-tls-pod.crt').encode('ascii'),
        DEFAULT_BACKEND)

    root_ca_cert_in_truststore = _export_cert_from_task_keystore(
        task_id, 'keystore.truststore', 'dcos-root')

    # Check that certificate subject maches the service name
    common_name = end_entity_cert.subject.get_attributes_for_oid(
        NameOID.COMMON_NAME)[0].value
    assert common_name in sdk_hosts.autoip_host(PACKAGE_NAME, 'artifacts-0-node')

    san_extension = end_entity_cert.extensions.get_extension_for_oid(
        ExtensionOID.SUBJECT_ALTERNATIVE_NAME)
    sans = san_extension.value._general_names._general_names
    assert len(sans) == 1

    cluster_root_ca_cert = x509.load_pem_x509_certificate(
        sdk_cmd.request(
            'get', shakedown.dcos_url_path('/ca/dcos-ca.crt')).content,
        DEFAULT_BACKEND)

    assert root_ca_cert_in_truststore.signature == cluster_root_ca_cert.signature


@pytest.mark.tls
@pytest.mark.sanity
@sdk_utils.dcos_1_10_or_higher
def test_java_keystore(hello_world_service):
    """
    Java `keystore-app` presents itself with provided TLS certificate
    from keystore.
    """
    task_id = sdk_tasks.get_task_ids(PACKAGE_NAME, 'artifacts')[0]
    assert task_id

    # Make a curl request from artifacts container to `keystore-app`
    # and make sure that mesos curl can verify certificate served by app
    curl = (
        'curl -v -i '
        '--cacert secure-tls-pod.ca '
        'https://' + sdk_hosts.vip_host(
            PACKAGE_NAME, KEYSTORE_TASK_HTTPS_PORT_NAME) + '/hello-world'
        )

    output = task_exec(task_id, curl, return_stderr_in_stdout=True)
    # Check that HTTP request was successful with response 200 and make sure
    # that curl with pre-configured cert was used and that task was matched
    # by SAN in certificate.
    assert 'HTTP/1.1 200 OK' in output
    assert 'CAfile: secure-tls-pod.ca' in output
    tls_verification_msg = (
        'host "keystore-https.hello-world.l4lb.thisdcos.directory" matched '
        'cert\'s "keystore-https.hello-world.l4lb.thisdcos.directory"'
    )
    assert tls_verification_msg in output


@pytest.mark.tls
@pytest.mark.sanity
@sdk_utils.dcos_1_10_or_higher
def test_tls_nginx(hello_world_service):
    """
    Checks that NGINX exposes TLS service with correct PEM encoded end-entity
    certificate.
    """

    # Use keystore-app `truststoretest` CLI command to run request against
    # the NGINX container to verify that nginx presents itself with end-entity
    # certificate that can be verified by with truststore.
    task_id = sdk_tasks.get_task_ids(PACKAGE_NAME, 'keystore')[0]
    assert task_id

    command = _java_command(
        'java -jar ' + KEYSTORE_APP_JAR_NAME + ' truststoretest '
        'integration-test.yml '
        'https://' + sdk_hosts.vip_host(
            PACKAGE_NAME, NGINX_TASK_HTTPS_PORT_NAME) + '/')
    output = task_exec(task_id, command)

    # Unfortunately the `dcos task exec` doesn't respect the return code
    # from executed command in container so we need to manually assert for
    # expected output.
    assert 'status=200' in output


@pytest.mark.tls
@pytest.mark.sanity
@sdk_utils.dcos_1_10_or_higher
def test_changing_discovery_replaces_certificate_sans(hello_world_service):
    """
    Update service configuration to change discovery prefix of a task.
    Scheduler should update task and new SANs should be generated.
    """
    task_id = sdk_tasks.get_task_ids(PACKAGE_NAME, "discovery")[0]
    assert task_id

    # Load end-entity certificate from PEM encoded file
    end_entity_cert = x509.load_pem_x509_certificate(
        task_exec(task_id, 'cat server.crt').encode('ascii'),
        DEFAULT_BACKEND)

    san_extension = end_entity_cert.extensions.get_extension_for_oid(
        ExtensionOID.SUBJECT_ALTERNATIVE_NAME)
    sans = [
        san.value for san in san_extension.value._general_names._general_names]

    expected_san = (
        '{name}-0.{package_name}.autoip.dcos.thisdcos.directory'.format(
            name=DISCOVERY_TASK_PREFIX,
            package_name=PACKAGE_NAME)
        )
    assert expected_san in sans

    # Run task update with new discovery prefix
    config = sdk_marathon.get_config(PACKAGE_NAME)
    config['env']['DISCOVERY_TASK_PREFIX'] = DISCOVERY_TASK_PREFIX + '-new'
    sdk_marathon.update_app(PACKAGE_NAME, config)

    new_task_id = sdk_tasks.get_task_ids(PACKAGE_NAME, "discovery")[0]
    assert task_id != new_task_id

    new_cert = x509.load_pem_x509_certificate(
        task_exec(new_task_id, 'cat server.crt').encode('ascii'),
        DEFAULT_BACKEND)

    san_extension = new_cert.extensions.get_extension_for_oid(
        ExtensionOID.SUBJECT_ALTERNATIVE_NAME)
    sans = [
        san.value for san in san_extension.value._general_names._general_names]

    expected_san =  (
        '{name}-0.{package_name}.autoip.dcos.thisdcos.directory'.format(
            name=DISCOVERY_TASK_PREFIX + '-new',
            package_name=PACKAGE_NAME)
        )
    assert expected_san in sans


def task_exec(task_name, command, **kwargs):
    return sdk_cmd.run_cli(
        "task exec {} {}".format(task_name, command), **kwargs)


def _export_cert_from_task_keystore(
        task, keystore_path, alias, password=KEYSTORE_PASS):
    """
    Retrieves certificate from the keystore with given alias by executing
    a keytool in context of running container and loads the certificate to
    memory.

    Args:
        task (str): Task id of container that contains the keystore
        keystore_path (str): Path inside container to keystore containing
            the certificate
        alias (str): Alias of the certificate in the keystore

    Returns:
        x509.Certificate object
    """
    args = ['-rfc']
    if password:
        args.append('-storepass "{password}"'.format(password=password))

    args_str = ' '.join(args)

    cert_bytes = task_exec(
        task, _keystore_export_command(keystore_path, alias, args_str)
    ).encode('ascii')

    return x509.load_pem_x509_certificate(
        cert_bytes, DEFAULT_BACKEND)


def _keystore_list_command(keystore_path, args=None):
    """
    Creates a command that can be executed using `dcos exec` CLI and will
    list certificates from provided keystore using java `keytool` command.

    https://docs.oracle.com/javase/8/docs/technotes/tools/windows/keytool.html

    Args:
        keystore_path (str): Path to the keystore file
        args (str): Optionally addiontal arguments for the `keytool -list`
            command.

    Returns:
        A string that can be used as `dcos exec` argument.
    """
    return _java_command(
        'keytool -list -keystore {keystore_path} '
        '-noprompt {args}'.format(
            keystore_path=keystore_path,
            args=args
        )
    )


def _keystore_export_command(keystore_path, cert_alias, args=None):
    """
    Runs the exportcert keytool command to export certificate with given alias.
    """
    return _java_command(
        'keytool -exportcert -keystore {keystore_path} '
        '-alias {alias} {args}'.format(
            keystore_path=keystore_path,
            alias=cert_alias,
            args=args
        )
    )


def _java_command(command):
    """
    General wrapper that can execute java command in container with SDK
    injected java binary.

    Args:
        command (str): Command that should be executed

    Returns:
        Bash wrapped command that initializes JAVA environment for executing
        the java command.
    """
    return (
        "bash -c ' "
        "export JAVA_HOME=$(ls -d $MESOS_SANDBOX/jre*/); "
        "export JAVA_HOME=${{JAVA_HOME%/}}; "
        "export PATH=$(ls -d $JAVA_HOME/bin):$PATH; "
        "{command}"
        "'"
    ).format(command=command)
