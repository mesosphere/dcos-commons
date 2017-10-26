"""
Wrapper script around sdk_auth.py used to ad-hoc setup and tear down a KDC environment.

This assumes there will be only one KDC in the cluster at any time, and thus that only instance
of the KDC will be aptly named `kdc`.

This tool expects as its arguments:
    - subcommand for setup or teardown
    - path to file holding principals as newline-separated strings
"""
import os
import sys
from subprocess import run

import sdk_auth

usage_msg = """Usage:
    python kdc -h, --help
    python kdc setup <path-to-principals-file>
    python kdc teardown
"""

verbose_usage_msg = """Usage:
    python kdc -h, --help
    python kdc setup <path-to-principals-file>
    python kdc teardown
    
    - <Path to principals file> is a path to a file listing the principals
    as newline-separated strings.
"""

def parse_principals(principals_file: str) -> list:
    """
    Parses the given file and extracts the list of principals.
    :param principals_file: The file to extract principals from.
    :return (str): List of principals.
    """
    if not os.path.exists(principals_file):
        print(principals_file)
        raise RuntimeError("The provided principal file path is invalid")

    principals = []
    with open(principals_file) as f:
        lines = f.readlines()
        for line in lines:
            principals.append(line)

    print("Successfully parsed principals")
    for principal in principals:
        print(principal)

    return principals


def main(args):
    if args[0] in ["-h", "--help"]:
        print(verbose_usage_msg)
        return 1

    if args[0] not in ["deploy", "teardown"]:
        print(usage_msg)
        return 1

    if args[0] == "deploy":
        try:
            principals = parse_principals(args[1])
        except Exception as e:
            print(repr(e))
            return 1

        try:
            kerberos = sdk_auth.KerberosEnvironment()
            kerberos.add_principals(principals)
            kerberos.finalize_environment()
        except Exception as e:
            print(repr(e))
            return 1

        print("\nSuccessfully deployed the KDC instance")

    elif args[0] == "teardown":
        print("Removing the KDC marathon app and keytab secret")
        try:
            run(["dcos", "marathon", "app", "remove", "kdc"])
            run(["dcos", "package", "install", "--yes", "--cli", "dcos-enterprise-cli"])
            run(["dcos", "security", "secrets", "delete", "__dcos_base64___keytab"])
        except Exception as e:
            print(repr(e))
            return 1
    
        print("\nSuccessfully teared down the KDC instance")

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
