'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_security IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging
import os
import retrying

from subprocess import check_output

from typing import Dict
from typing import List

import sdk_cmd
import sdk_utils

log = logging.getLogger(__name__)


DEFAULT_LINUX_USER = "nobody"


def install_enterprise_cli(force=False):
    """ Install the enterprise CLI if required """

    log.info("Installing DC/OS enterprise CLI")
    if not force:
        cmd = "security --version"
        _, stdout, _ = sdk_cmd.run_raw_cli(cmd, print_output=False)
        if stdout:
            log.info("DC/OS enterprise version %s CLI already installed", stdout.strip())
            return

    cmd = "package install --yes --cli dcos-enterprise-cli"

    @retrying.retry(stop_max_attempt_number=3,
                    wait_fixed=2000,
                    retry_on_result=lambda result: result)
    def _install_impl():
        rc, stdout, stderr = sdk_cmd.run_raw_cli(cmd)
        if rc:
            log.error("rc=%s stdout=%s stderr=%s", rc, stdout, stderr)

        return rc

    try:
        _install_impl()
    except Exception as e:
        raise RuntimeError("Failed to install the dcos-enterprise-cli: {}".format(repr(e)))


def _grant(user: str, acl: str, description: str, action: str="create") -> None:
    log.info('Granting permission to {user} for {acl}/{action} ({description})'.format(
        user=user, acl=acl, action=action, description=description))

    # Create the ACL
    r = sdk_cmd.cluster_request(
        'PUT', '/acs/api/v1/acls/{acl}'.format(acl=acl),
        raise_on_error=False,
        json={'description': description})
    # 201=created, 409=already exists
    assert r.status_code in [201, 409, ], '{} failed {}: {}'.format(r.url, r.status_code, r.text)

    # Assign the user to the ACL
    r = sdk_cmd.cluster_request(
        'PUT', '/acs/api/v1/acls/{acl}/users/{user}/{action}'.format(acl=acl, user=user, action=action),
        raise_on_error=False)
    # 204=success, 409=already exists
    assert r.status_code in [204, 409, ], '{} failed {}: {}'.format(r.url, r.status_code, r.text)


def _revoke(user: str, acl: str, description: str, action: str="create") -> None:
    # TODO(kwood): INFINITY-2065 - implement security cleanup
    log.info("Want to delete {user}+{acl}".format(user=user, acl=acl))


def get_default_permissions(service_account_name: str, role: str, linux_user: str) -> List[dict]:
    return [
        # registration permissions
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:framework:role:{}".format(role),
            'description': "Service {} may register with the Mesos master with role={}".format(
                service_account_name, role),
        },

        # task execution permissions
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:task:user:{}".format(linux_user),
            'description': "Service {} may execute Mesos tasks as user={}".format(
                service_account_name, linux_user)
        },

        # XXX 1.10 currently requires this mesos:agent permission as well as
        # mesos:task permission.  unclear if this will be ongoing requirement.
        # See DCOS-15682
        {
            'user': service_account_name,
            'acl': "dcos:mesos:agent:task:user:{}".format(linux_user),
            'description': "Service {} may execute Mesos tasks as user={}".format(
                service_account_name, linux_user)
        },

        # resource permissions
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:reservation:role:{}".format(role),
            'description': "Service {} may reserve Mesos resources with role={}".format(
                service_account_name, role)
        },
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:reservation:principal:{}".format(service_account_name),
            'description': "Service {} may reserve Mesos resources with principal={}".format(
                service_account_name, service_account_name),
            'action': "delete",
        },

        # volume permissions
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:volume:role:{}".format(role),
            'description': "Service {} may create Mesos volumes with role={}".format(
                service_account_name, role)
        },
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:volume:principal:{}".format(service_account_name),
            'description': "Service {} may create Mesos volumes with principal={}".format(
                service_account_name, service_account_name),
            'action': "delete",
        }]


def grant_permissions(linux_user: str, role_name: str, service_account_name: str, permissions: List[dict]) -> None:
    log.info("Granting permissions to {account}".format(account=service_account_name))

    if not permissions:
        permissions = get_default_permissions(service_account_name, role_name, linux_user)

    for permission in permissions:
        _grant(**permission)
    log.info("Permission setup completed for {account}".format(account=service_account_name))


def revoke_permissions(linux_user: str, role_name: str, service_account_name: str, permissions: List[dict]) -> None:
    log.info("Revoking permissions to {account}".format(account=service_account_name))

    if not permissions:
        permissions = get_default_permissions(service_account_name, role_name, linux_user)

    for permission in permissions:
        _revoke(**permission)
    log.info("Permission cleanup completed for {account}".format(account=service_account_name))


def create_service_account(service_account_name: str, service_account_secret: str) -> None:
    """
    Creates a service account. If it already exists, it is deleted.
    """
    install_enterprise_cli()

    log.info('Creating service account for account={account} secret={secret}'.format(
        account=service_account_name,
        secret=service_account_secret))

    log.info('Remove any existing service account and/or secret')
    delete_service_account(service_account_name, service_account_secret)

    log.info('Create keypair')
    sdk_cmd.run_cli('security org service-accounts keypair private-key.pem public-key.pem')

    log.info('Create service account')
    sdk_cmd.run_cli('security org service-accounts create -p public-key.pem '
                    '-d "Service account for integration tests" "{account}"'.format(account=service_account_name))

    log.info('Create secret')
    sdk_cmd.run_cli(
        'security secrets create-sa-secret --strict private-key.pem "{account}" "{secret}"'.format(
            account=service_account_name, secret=service_account_secret))

    log.info('Service account created for account={account} secret={secret}'.format(
        account=service_account_name,
        secret=service_account_secret))


def delete_service_account(service_account_name: str, service_account_secret: str) -> None:
    """
    Deletes service account with private key that belongs to the service account.
    """
    # ignore any failures:
    sdk_cmd.run_cli("security org service-accounts delete {name}".format(name=service_account_name))

    # Files generated by service-accounts keypair command should get removed
    for keypair_file in ['private-key.pem', 'public-key.pem']:
        try:
            os.unlink(keypair_file)
        except OSError:
            pass

    delete_secret(secret=service_account_secret)


def delete_secret(secret: str) -> None:
    """
    Deletes a given secret.
    """
    # ignore any failures:
    sdk_cmd.run_cli("security secrets delete {}".format(secret))


def _get_role_list(service_name: str) -> List[str]:
    # TODO: spark_utils uses:
    # app_id_encoded = urllib.parse.quote(
    #     urllib.parse.quote(app_id, safe=''),
    #     safe=''
    # )
    role_basename = service_name.strip("/").replace("/", "__")
    return [
        "{}-role".format(role_basename),
        "slave_public%252F{}-role".format(role_basename),
    ]


def setup_security(service_name: str,
                   permissions: List[dict]=[],
                   linux_user: str=DEFAULT_LINUX_USER,
                   service_account: str="service-acct",
                   service_account_secret: str="secret") -> dict:

    create_service_account(service_account_name=service_account,
                           service_account_secret=service_account_secret)

    service_account_info = {"name": service_account,
                            "secret": service_account_secret,
                            "linux_user": linux_user,
                            "roles": [],
                            "permissions": [],
                            }

    if not sdk_utils.is_strict_mode():
        log.info("Skipping strict-mode security setup on non-strict cluster")
        return service_account_info

    log.info("Setting up strict-mode security")

    service_account_info["permissions"] = permissions
    service_account_info["roles"] = _get_role_list(service_name)

    for role_name in service_account_info["roles"]:
        grant_permissions(
            linux_user=linux_user,
            role_name=role_name,
            service_account_name=service_account,
            permissions=permissions,
        )

    log.info("Finished setting up strict-mode security")

    return service_account_info


def cleanup_security(service_name: str,
                     service_account_info: Dict) -> None:

    service_account = service_account_info.get("name", "service-acct")
    service_account_secret = service_account_info.get("secret", "secret")

    log.info("Cleaning up strict-mode security")

    linux_user = service_account_info.get("linux_user", DEFAULT_LINUX_USER)
    permissions = service_account_info.get("permissions", [])
    roles = service_account_info.get("roles", _get_role_list(service_name))

    for role_name in roles:
        revoke_permissions(
            linux_user=linux_user,
            role_name=role_name,
            service_account_name=service_account,
            permissions=permissions
        )

    delete_service_account(service_account, service_account_secret)

    log.info('Finished cleaning up strict-mode security')


def security_session(framework_name: str,
                     permissions: List[dict]=[],
                     linux_user: str=DEFAULT_LINUX_USER,
                     service_account: str="service-acct",
                     service_account_secret: str="secret") -> None:
    """Create a service account and configure permissions for strict-mode tests.

    This should generally be used as a fixture in a framework's conftest.py:

    @pytest.fixture(scope='session')
    def configure_security(configure_universe):
        yield from sdk_security.security_session(framework_name, permissions, linux_user, 'service-acct')
    """
    try:
        is_strict = sdk_utils.is_strict_mode()
        if is_strict:
            service_account_info = setup_security(framework_name,
                                                  permissions,
                                                  linux_user,
                                                  service_account,
                                                  service_account_secret)
        yield
    finally:
        if is_strict:
            cleanup_security(framework_name, service_account_info)


def openssl_ciphers():
    return set(
        check_output(['openssl', 'ciphers',
                      'ALL:eNULL']).decode('utf-8').rstrip().split(':'))


def is_cipher_enabled(service_name: str,
                      task_name: str,
                      cipher: str,
                      endpoint: str,
                      openssl_timeout: str = '1') -> bool:
    @retrying.retry(stop_max_attempt_number=3,
                    wait_fixed=2000,
                    retry_on_result=lambda result: 'Failed to enter mount namespace' in result)
    def run_openssl_command() -> str:
        command = ' '.join([
            'timeout', openssl_timeout,
            'openssl', 's_client', '-cipher', cipher, '-connect', endpoint
        ])

        _, output = sdk_cmd.service_task_exec(service_name, task_name, command, True)
        return output

    output = run_openssl_command()

    return "Cipher is {}".format(cipher) in output
