#!/usr/bin/env python3

"""
Wrapper script around sdk_auth.py used to ad-hoc setup and tear down a KDC environment.

This assumes there will be only one KDC in the cluster at any time, and thus that only instance
of the KDC will be aptly named `kdc`.

In order to run the script from the `dcos-commons` repo root, the `PYTHONPATH` environment
variable must also be set:
```bash
$ PYTHONPATH=testing ./tools/kdc/kdc.py SUBCOMMAND
```

## Deploying KDC

This tool can be used to deploy a KDC applciation for testing.

First create a principals file containing a newline-separated list of principals.
As an example, a file (`kafka-principals.txt`) for Apache Kafka would contain:
```
kafka/kafka-0-broker.kafka.autoip.dcos.thisdcos.directory@LOCAL
kafka/kafka-1-broker.kafka.autoip.dcos.thisdcos.directory@LOCAL
kafka/kafka-2-broker.kafka.autoip.dcos.thisdcos.directory@LOCAL
client@LOCAL
```
(assuming three Kafka brokers and a single client principal)

Running this utility as follows:
```bash
$ PYTHONPATH=testing ./tools/kdc/kdc.py deploy kafka-principals.txt
```
will perform the following actions:
1. Deploys a KDC Marathon application named `kdc` as defined in `tools/kdc/kdc.json`
2. Adds the principals in `kafka-principals.txt` to the KDC store
3. Saves the generated keytab as the DC/OS secret `__dcos_base64___keytab`

## Removing KDC

This tool can be used to remove an existing KDC deployment.

Running this utility as follows:
```bash
$ PYTHONPATH=testing ./tools/kdc/kdc.py
```
will perform the following actions:
1. Remove the KDC Marathoin application named `kdc`
2. Remove the DC/OS secret `__dcos_base64___keytab`


Note: The KDC this tool launches uses the following encryption key types:
- aes256-cts-hmac-sha1-96
- des3-cbc-sha1
- arcfour-hmac-md5
"""
import argparse
import logging
import os

import sdk_auth
import sdk_cmd
import sdk_security


logging.basicConfig(format="[%(asctime)s|%(name)s|%(levelname)s]: %(message)s", level=logging.INFO)

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
        principals = [principal.strip() for principal in f.readlines()]

    print("Successfully parsed principals")
    for principal in principals:
        print(principal)

    return principals


def deploy(args: dict):
    log.info("Deploying KDC")

    kerberos = sdk_auth.KerberosEnvironment(persist=True)

    if args.principals_file:
        create_keytab_secret(args, kerberos)

    log.info("KDC cluster successfully deployed")


def create_keytab_secret(args: dict, kerberos=None):

    if not kerberos:
        kerberos = sdk_auth.KerberosEnvironment(persist=True)

    principals = parse_principals(args.principals_file)
    kerberos.add_principals(principals)

    if args.secret_name:
        kerberos.set_keytab_path(args.secret_name, args.binary_secret)

    kerberos.finalize()

    log.info("KDC cluster successfully deployed")


def teardown(args: dict):
    log.info("Tearing down KDC")

    sdk_cmd.run_cli(" ".join(["marathon", "app", "remove", "kdc"]))

    sdk_security.install_enterprise_cli()
    if args.binary_secret:
        sdk_security.delete_secret(args.secret_name)
    else:
        sdk_security.delete_secret("__dcos_base64__{}".format(args.secret_name))

    log.info("KDC cluster successfully torn down")


def parse_args():
    parser = argparse.ArgumentParser(description="Manage a KDC instance")

    parser.add_argument(
        "--secret-name",
        type=str,
        required=False,
        default=None,
        help="The secret name to use for the generated keytab",
    )
    parser.add_argument(
        "--binary-secret",
        action="store_true",
        help="The secret should be stored as a binary secret",
    )
    subparsers = parser.add_subparsers(help="deploy help")

    deploy_parser = subparsers.add_parser("deploy", help="deploy help")
    deploy_parser.add_argument(
        "principals_file",
        type=str,
        default=None,
        help="Path to a file listing the principals as newline-separated strings",
    )
    deploy_parser.set_defaults(func=deploy)

    teardown_parser = subparsers.add_parser("teardown", help="deploy help")
    teardown_parser.set_defaults(func=teardown)

    return parser.parse_args()


def main():
    args = parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
