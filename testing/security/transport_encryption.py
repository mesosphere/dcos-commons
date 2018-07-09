"""
A collection of utilities used for SSL tests.
"""
import json
import logging


import sdk_cmd
import sdk_security
import sdk_utils

log = logging.getLogger(__name__)


def setup_service_account(service_name: str,
                          service_account_secret: str=None) -> dict:
    """
    Setup the service account for TLS. If the account or secret of the specified
    name already exists, these are deleted.
    """

    if sdk_utils.is_open_dcos():
        log.error("The setup of a service account requires DC/OS EE. service_name=%s", service_name)
        raise Exception("The setup of a service account requires DC/OS EE")

    name = service_name
    secret = name if service_account_secret is None else service_account_secret

    service_account_info = sdk_security.setup_security(service_name,
                                                       service_account=name,
                                                       service_account_secret=secret)

    log.info("Adding permissions required for TLS.")
    if sdk_utils.dcos_version_less_than("1.11"):
        sdk_cmd.run_cli("security org groups add_user superusers {name}".format(name=name))
    else:
        acls = [
            {"rid": "dcos:secrets:default:/{}/*".format(service_name.strip("/")), "action": "full"},
            {"rid": "dcos:secrets:list:default:/{}".format(service_name.strip("/")), "action": "read"},
            {"rid": "dcos:adminrouter:ops:ca:rw", "action": "full"},
            {"rid": "dcos:adminrouter:ops:ca:ro", "action": "full"},
        ]

        for acl in acls:
            cmd_list = ["security", "org", "users", "grant",
                        "--description", "\"Permissing required to provision TLS certificates\"",
                        name, acl["rid"], acl["action"]
                        ]

            output = sdk_cmd.run_cli(" ".join(cmd_list))
            log.info("output=%s", output)

    return service_account_info


def cleanup_service_account(service_name: str, service_account_info: dict):
    """
    Clean up the specified service account.

    Ideally, this service account was created using the setup_service_account function.
    """
    if isinstance(service_account_info, str):
        service_account_info = {"name": service_account_info}

    name = service_account_info["name"]
    secret = service_account_info["secret"] if "secret" in service_account_info else name

    sdk_security.cleanup_security(service_name,
                                  service_account=name,
                                  service_account_secret=secret)


def fetch_dcos_ca_bundle(task: str) -> str:
    """Fetch the DC/OS CA bundle from the leading Mesos master"""
    local_bundle_file = "dcos-ca.crt"

    cmd = ["curl", "-L", "--insecure", "-v",
           "leader.mesos/ca/dcos-ca.crt",
           "-o", local_bundle_file]

    sdk_cmd.task_exec(task, " ".join(cmd))

    return local_bundle_file


def fetch_dcos_ca_bundle_contents() -> str:
    resp = sdk_cmd.cluster_request("GET", "/ca/dcos-ca.crt")
    cert = resp.content
    if not cert:
        log.error("Error fetching DC/OS CA bundle")
        raise Exception("Errot fetching DC/OS CA bundle")

    return cert


def create_tls_artifacts(cn: str, task: str) -> str:
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
    pub_path = "{}_pub.crt".format(cn)
    priv_path = "{}_priv.key".format(cn)
    keystore_path = "{}_keystore.jks".format(cn)
    truststore_path = "{}_truststore.jks".format(cn)

    log.info("Generating keystore and truststore, task:{}".format(task))
    dcos_ca_bundle = fetch_dcos_ca_bundle(task)

    # Convert to a PKCS12 key
    output = sdk_cmd.task_exec(
        task,
        'bash -c "export RANDFILE=/mnt/mesos/sandbox/.rnd && '
        'openssl pkcs12 -export -in {} -inkey {} '
        '-out keypair.p12 -name keypair -passout pass:export '
        '-CAfile {} -caname root"'.format(pub_path, priv_path, dcos_ca_bundle))
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
        "-file {} -storepass changeit "
        "-keystore {}".format(dcos_ca_bundle, truststore_path))
    log.info(output)
    assert output[0] is 0
