"""
Authnz specifics for HDFS tests
"""
import logging
import base64

from security import kerberos

import sdk_hosts


log = logging.getLogger(__name__)


USERS = ["hdfs", "alice", "bob"]


def get_service_principals(service_name: str, realm: str) -> list:
    """
    Sets up the appropriate principals needed for a kerberized deployment of HDFS.
    :return: A list of said principals
    """
    primaries = ["hdfs", "HTTP"]

    tasks = [
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
    instances = map(lambda task: sdk_hosts.autoip_host(service_name, task), tasks)

    principals = kerberos.generate_principal_list(primaries, instances, realm)
    principals.extend(kerberos.generate_principal_list(USERS, [None], realm))

    http_instance = sdk_hosts.vip_host("marathon", ".".join(["api", service_name]))
    http_principal = kerberos.genererate_principal("HTTP", http_instance, realm)
    principals.append(http_principal)

    return principals


def get_principal_to_user_mapping() -> str:
    """
    Kerberized HDFS maps the primary component of a principal to local users, so
    we need to create an appropriate mapping to test authorization functionality.
    :return: A base64-encoded string of principal->user mappings
    """
    rules = ["RULE:[2:$1@$0](^hdfs@.*$)s/.*/hdfs/"]

    for user in USERS:
        rules.append("RULE:[1:$1@$0](^{user}@.*$)s/.*/{user}/".format(user=user))

    return base64.b64encode("\n".join(rules).encode("utf-8")).decode("utf-8")
