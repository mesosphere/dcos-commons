"""
Wrapper script around sdk_auth.py used to ad-hoc setup and tear down a KDC environment.

This assumes there will be only one KDC in the cluster at any time, and thus that only instance
of the KDC will be aptly named `kdc`.

This tool expects as its arguments:
    - subcommand for setup or teardown
    - path to file holding principals as newline-separated strings
"""
import argparse
import logging
import os

import sdk_auth
import sdk_cmd


logging.basicConfig(
    format='[%(asctime)s|%(name)s|%(levelname)s]: %(message)s',
    level=logging.INFO)

log = logging.getLogger(__name__)


def parse_principals(principals_file: str) -> list:
    """
    Parses the given file and extracts the list of principals.
    :param principals_file: The file to extract principals from.
    :return (str): List of principals.
    """
    if not os.path.exists(principals_file):
        print(principals_file)
        raise RuntimeError("The provided principal file path is invalid")

    with open(principals_file) as f:
        principals = f.readlines()

    print("Successfully parsed principals")
    for principal in principals:
        print(principal)

    return principals


def deploy(args: dict):
    log.info("Deploying KDC")

    principals = parse_principals(args.principals_file)

    kerberos = sdk_auth.KerberosEnvironment()
    kerberos.add_principals(principals)
    kerberos.finalize_environment()

    log.info("KDC cluster successfully deployed")


def teardown(args: dict):
    log.info("Tearing down KDC")

    sdk_cmd.run_cli(["marathon", "app", "remove", "kdc"])
    sdk_cmd.run_cli(["package", "install", "--yes", "--cli", "dcos-enterprise-cli"])
    sdk_cmd.run_cli(["security", "secrets", "delete", "__dcos_base64___keytab"])

    log.info("KDC cluster successfully torn down")


def parse_args():
    parser = argparse.ArgumentParser(description='Manage a KDC instance')

    subparsers = parser.add_subparsers(help='deploy help')

    deploy_parser = subparsers.add_parser('deploy', help='deploy help')
    deploy_parser.add_argument('principals_file', type=str,
                               help='Path to a file listing the principals as newline-separated strings')
    deploy_parser.set_defaults(func=deploy)

    teardown_parser = subparsers.add_parser('teardown', help='deploy help')
    teardown_parser.set_defaults(func=teardown)

    return parser.parse_args()


def main():
    args = parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
