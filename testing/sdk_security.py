'''
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_security IN ANY OTHER PARTNER REPOS
************************************************************************
'''
import logging
import os
from typing import List, Tuple

import retrying
import requests
import shakedown
import sdk_cmd
import sdk_utils

log = logging.getLogger(__name__)


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


def grant(dcosurl: str, headers: dict, user: str, acl: str, description: str, action: str="create") -> None:
    log.info('Granting permission to {user} for {acl}/{action} ({description})'.format(
        user=user, acl=acl, action=action, description=description))

    # TODO(kwood): INFINITY-2066 - Use dcos_test_utils instead of raw requests

    # Create the ACL
    create_endpoint = '{dcosurl}/acs/api/v1/acls/{acl}'.format(dcosurl=dcosurl, acl=acl)
    r = requests.put(create_endpoint, headers=headers, json={'description': description}, verify=False)
    # 201=created, 409=already exists
    assert r.status_code == 201 or r.status_code == 409, '{} failed {}: {}'.format(
        create_endpoint, r.status_code, r.text)

    # Assign the user to the ACL
    assign_endpoint = '{dcosurl}/acs/api/v1/acls/{acl}/users/{user}/{action}'.format(
        dcosurl=dcosurl, acl=acl, user=user, action=action)
    r = requests.put(assign_endpoint, headers=headers, verify=False)
    # 204=success, 409=already exists
    assert r.status_code == 204 or r.status_code == 409, '{} failed {}: {}'.format(
        create_endpoint, r.status_code, r.text)


def revoke(dcosurl: str, headers: dict, user: str, acl: str, description: str, action: str="create") -> None:
    # TODO(kwood): INFINITY-2065 - implement security cleanup
    log.info("Want to delete {user}+{acl}".format(user=user, acl=acl))


def get_dcos_credentials() -> Tuple[str, dict]:
    dcosurl = sdk_cmd.run_cli('config show core.dcos_url', print_output=False)
    token = sdk_cmd.run_cli('config show core.dcos_acs_token', print_output=False)
    headers = {
        'Content-Type': 'application/json',
        'Authorization': 'token={}'.format(token.strip()),
    }
    return dcosurl.strip(), headers


def get_permissions(service_account_name: str, role: str, linux_user: str) -> List[dict]:
    return [
        ## registration permissions
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:framework:role:{}".format(role),
            'description': "Service {} may register with the Mesos master with role={}".format(
                service_account_name, role),
        },

        ## task execution permissions
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:task:user:{}".format(linux_user),
            'description': "Service {} may execute Mesos tasks as user={}".format(
                service_account_name, linux_user)
        },

        # XXX 1.10 curerrently requires this mesos:agent permission as well as
        # mesos:task permission.  unclear if this will be ongoing requirement.
        # See DCOS-15682
        {
            'user': service_account_name,
            'acl': "dcos:mesos:agent:task:user:{}".format(linux_user),
            'description': "Service {} may execute Mesos tasks as user={}".format(
                service_account_name, linux_user)
        },

        ## resource permissions
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

        ## volume permissions
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


def grant_permissions(linux_user: str, role_name: str, service_account_name: str) -> None:
    dcosurl, headers = get_dcos_credentials()
    log.info("Granting permissions to {account}".format(account=service_account_name))
    permissions = get_permissions(service_account_name, role_name, linux_user)
    for permission in permissions:
        grant(dcosurl, headers, **permission)
    log.info("Permission setup completed for {account}".format(account=service_account_name))


def revoke_permissions(linux_user: str, role_name: str, service_account_name: str) -> None:
    dcosurl, headers = get_dcos_credentials()
    # log.info("Revoking permissions to {account}".format(account=service_account_nae))
    permissions = get_permissions(service_account_name, role_name, linux_user)
    for permission in permissions:
        revoke(dcosurl, headers, **permission)
    # log.info("Permission cleanup completed for {account}".format(account=service_account_name))


def create_service_account(service_account_name: str, service_account_secret: str) -> None:
    log.info('Creating service account for account={account} secret={secret}'.format(
        account=service_account_name,
        secret=service_account_secret))

    log.info('Install cli necessary for security')
    sdk_cmd.run_cli('package install dcos-enterprise-cli --yes')

    log.info('Remove any existing service account and/or secret')
    delete_service_account(service_account_name, service_account_secret)

    log.info('Create keypair')
    sdk_cmd.run_cli('security org service-accounts keypair private-key.pem public-key.pem')

    log.info('Create service account')
    sdk_cmd.run_cli(
        'security org service-accounts create -p public-key.pem -d "Service account for integration tests" "{account}"'.format(
            account=service_account_name))

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


def setup_security(framework_name: str) -> None:
    log.info('Setting up strict-mode security')
    create_service_account(service_account_name='service-acct', service_account_secret='secret')
    grant_permissions(
        linux_user='nobody',
        role_name='{}-role'.format(framework_name),
        service_account_name='service-acct'
    )
    grant_permissions(
        linux_user='nobody',
        role_name='test__integration__{}-role'.format(framework_name),
        service_account_name='service-acct'
    )
    log.info('Finished setting up strict-mode security')


def cleanup_security(framework_name: str) -> None:
    log.info('Cleaning up strict-mode security')
    revoke_permissions(
        linux_user='nobody',
        role_name='{}-role'.format(framework_name),
        service_account_name='service-acct'
    )
    revoke_permissions(
        linux_user='nobody',
        role_name='test__integration__{}-role'.format(framework_name),
        service_account_name='service-acct'
    )
    delete_service_account('service-acct', 'secret')
    log.info('Finished cleaning up strict-mode security')


def security_session(framework_name: str) -> None:
    """Create a service account and configure permissions for strict-mode tests.

    This should generally be used as a fixture in a framework's conftest.py:

    @pytest.fixture(scope='session')
    def configure_security(configure_universe):
        yield from sdk_security.security_session(framework_name)
    """
    try:
        is_strict = sdk_utils.is_strict_mode()
        if is_strict:
            setup_security(framework_name)
        yield
    finally:
        if is_strict:
            cleanup_security(framework_name)
