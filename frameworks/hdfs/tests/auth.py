"""
Authnz specifics for HDFS tests
"""
import itertools
import logging
import base64
import json


import sdk_cmd
import sdk_hosts


log = logging.getLogger(__name__)


USERS = [
    "hdfs",
    "alice",
    "bob",
]


def get_service_principals(service_name: str, realm: str) -> list:
    """
    Sets up the appropriate principals needed for a kerberized deployment of HDFS.
    :return: A list of said principals
    """
    primaries = ["hdfs", "HTTP"]
    fqdn = sdk_hosts.autoip_host(service_name, "")

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
                REALM=realm
            )
        )

    for primary in USERS:
        principals.append(
            "{primary}@{REALM}".format(
                primary=primary,
                REALM=realm
            )
        )

    http_principal = "HTTP/api.{}.marathon.l4lb.thisdcos.directory".format(service_name)

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

    for user in USERS:
        rules.append("RULE:[1:$1@$0](^{user}@.*$)s/.*/{user}/".format(user=user))

    return base64.b64encode('\n'.join(rules).encode("utf-8")).decode("utf-8")


def write_krb5_config_file(task: str, filename: str, krb5: object) -> str:
    """
    Generate a Kerberos config file.
    TODO(elezar): This duplicates functionality in frameworks/kafka/tests/auth.py
    TODO(elezar): Move to testing/security/kerberos.py
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


def create_tls_artifacts(cn: str, task: str) -> str:
    # TODO(elezar) Move to testing/security/ssl
    pub_path = "{}_pub.crt".format(cn)
    priv_path = "{}_priv.key".format(cn)
    log.info("Generating certificate. cn={}, task={}".format(cn, task))

    output = sdk_cmd.task_exec(
        task,
        'openssl req -nodes -newkey rsa:2048 -keyout {} -out request.csr '
        '-subj "/C=US/ST=CA/L=SF/O=Mesosphere/OU=Mesosphere/CN={}"'.format(priv_path, cn))
    log.info(output)
    assert output[0] is 0

    rc, raw_csr, _ = sdk_cmd.task_exec(task, 'cat request.csr')
    assert rc is 0
    request = {
        "certificate_request": raw_csr
    }

    token = sdk_cmd.run_cli("config show core.dcos_acs_token")

    output = sdk_cmd.task_exec(
        task,
        "curl --insecure -L -X POST "
        "-H 'Authorization: token={}' "
        "leader.mesos/ca/api/v2/sign "
        "-d '{}'".format(token, json.dumps(request)))
    log.info(output)
    assert output[0] is 0

    # Write the public cert to the client
    certificate = json.loads(output[1])["result"]["certificate"]
    output = sdk_cmd.task_exec(task, "bash -c \"echo '{}' > {}\"".format(certificate, pub_path))
    log.info(output)
    assert output[0] is 0

    create_keystore_truststore(cn, task)
    return "CN={},OU=Mesosphere,O=Mesosphere,L=SF,ST=CA,C=US".format(cn)


def create_keystore_truststore(cn: str, task: str):
    # TODO(elezar) Move to testing/security/ssl
    pub_path = "{}_pub.crt".format(cn)
    priv_path = "{}_priv.key".format(cn)
    keystore_path = "{}_keystore.jks".format(cn)
    truststore_path = "{}_truststore.jks".format(cn)

    log.info("Generating keystore and truststore, task:{}".format(task))
    output = sdk_cmd.task_exec(task, "curl -L -k -v leader.mesos/ca/dcos-ca.crt -o dcos-ca.crt")

    # Convert to a PKCS12 key
    output = sdk_cmd.task_exec(
        task,
        'bash -c "export RANDFILE=/mnt/mesos/sandbox/.rnd && '
        'openssl pkcs12 -export -in {} -inkey {} '
        '-out keypair.p12 -name keypair -passout pass:export '
        '-CAfile dcos-ca.crt -caname root"'.format(pub_path, priv_path))
    log.info(output)
    assert output[0] is 0

    log.info("Generating certificate: importing into keystore and truststore")
    # Import into the keystore and truststore
    output = sdk_cmd.task_exec(
        task,
        "keytool -importkeystore "
        "-deststorepass changeit -destkeypass changeit -destkeystore {} "
        "-srckeystore keypair.p12 -srcstoretype PKCS12 -srcstorepass export "
        "-alias keypair".format(keystore_path))
    log.info(output)
    assert output[0] is 0

    output = sdk_cmd.task_exec(
        task,
        "keytool -import -trustcacerts -noprompt "
        "-file dcos-ca.crt -storepass changeit "
        "-keystore {}".format(truststore_path))
    log.info(output)
    assert output[0] is 0
