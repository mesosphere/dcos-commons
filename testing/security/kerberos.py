"""
A set of Kerberos utilities
"""
import itertools
import logging

import sdk_cmd


log = logging.getLogger(__name__)


def genererate_principal(primary: str, instance: str, realm: str) -> str:
    """
    Generate a Kerberos principal from the three different components.
    """
    if instance:
        principal = "{}/{}".format(primary, instance)
    else:
        principal = primary

    return "{}@{}".format(principal, realm.upper())


def generate_principal_list(primaries: list, instances: list, realm: str) -> list:
    principals = []
    for (primary, instance) in itertools.product(primaries, instances):
        principals.append(genererate_principal(primary, instance, realm))

    return principals


def write_krb5_config_file(task: str, filename: str, krb5: object) -> str:
    """
    Generate a Kerberos config file.
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
