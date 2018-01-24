"""
Authnz specifics for HDFS tests
"""
import itertools
import logging
import base64


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
            "{primary}/{instance}{fqdn}@{REALM}".format(
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
