import logging
import os
from typing import List, Tuple

import requests
import shakedown

log = logging.getLogger(__name__)


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
    # log.info("Want to delete {user}+{acl}".format(user=user, acl=acl))
    pass


def get_dcos_credentials() -> Tuple[str, dict]:
    dcosurl, err, rc = shakedown.run_dcos_command('config show core.dcos_url')
    assert not rc, "Cannot get core.dcos_url: {}".format(err)
    token, err, rc = shakedown.run_dcos_command('config show core.dcos_acs_token')
    assert not rc, "Cannot get dcos_acs_token: {}".format(err)
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
            'description': "Register with the Mesos master with role={}".format(role),
        },

        ## task execution permissions
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:task:user:{}".format(linux_user),
            'description': "Execute Mesos tasks as user={}".format(linux_user)
        },

        # XXX 1.10 curerrently requires this mesos:agent permission as well as
        # mesos:task permission.  unclear if this will be ongoing requirement.
        # See DCOS-15682
        {
            'user': service_account_name,
            'acl': "dcos:mesos:agent:task:user:{}".format(linux_user),
            'description': "Execute Mesos tasks as user={}".format(linux_user)
        },

        # In order for the Spark Dispatcher to register with Mesos as
        # root, we must launch the dispatcher task as root.  The other
        # frameworks are launched as nobody, but then register as
        # service.user, which defaults to root
        {
            'user': 'dcos_marathon',
            'acl': "dcos:mesos:master:task:user:root",
            'description': "Execute Mesos tasks as user=root"
        },

        # XXX see above
        {
            'user': 'dcos_marathon',
            'acl': "dcos:mesos:agent:task:user:root",
            'description': "Execute Mesos tasks as user=root"
        },

        ## resource permissions
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:reservation:role:{}".format(role),
            'description': "Reserve Mesos resources with role={}".format(role)
        },
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:reservation:principal:{}".format(service_account_name),
            'description': "Reserve Mesos resources with principal={}".format(service_account_name),
            'action': "delete",
        },

        ## volume permissions
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:volume:role:{}".format(role),
            'description': "Create Mesos volumes with role={}".format(role)
        },
        {
            'user': service_account_name,
            'acl': "dcos:mesos:master:volume:principal:{}".format(service_account_name),
            'description': "Create Mesos volumes with principal={}".format(service_account_name),
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
    # log.info("Revoking permissions to {account}".format(account=service_account_name))
    permissions = get_permissions(service_account_name, role_name, linux_user)
    for permission in permissions:
        revoke(dcosurl, headers, **permission)
    # log.info("Permission cleanup completed for {account}".format(account=service_account_name))


def create_service_account(service_account_name: str, service_account_secret: str) -> None:
    log.info('Creating service account for account={account} secret={secret}'.format(
        account=service_account_name,
        secret=service_account_secret))

    log.info('Install cli necessary for security')
    out, err, rc = shakedown.run_dcos_command('package install dcos-enterprise-cli --package-version=1.0.7')
    assert not rc, 'Failed to install dcos-enterprise cli extension: {err}'.format(err=err)

    log.info('Create keypair')
    out, err, rc = shakedown.run_dcos_command('security org service-accounts keypair private-key.pem public-key.pem')
    assert not rc, 'Failed to create keypair for testing service account: {err}'.format(err=err)

    log.info('Create service account')
    out, err, rc = shakedown.run_dcos_command(
        'security org service-accounts delete "{account}"'.format(account=service_account_name))
    out, err, rc = shakedown.run_dcos_command(
        'security org service-accounts create -p public-key.pem -d "My service account" "{account}"'.format(
            account=service_account_name))
    assert not rc, 'Failed to create service account "{account}": {err}'.format(
            account=service_account_name, err=err)

    log.info('Create secret')
    out, err, rc = shakedown.run_dcos_command('security secrets delete "{secret}"'.format(secret=service_account_secret))
    out, err, rc = shakedown.run_dcos_command(
        'security secrets create-sa-secret --strict private-key.pem "{account}" "{secret}"'.format(
            account=service_account_name, secret=service_account_secret))
    assert not rc, 'Failed to create secret "{secret}" for service account "{account}": {err}'.format(
            account=service_account_name,
            secret=service_account_secret,
            err=err)

    log.info('Service account created for account={account} secret={secret}'.format(
        account=service_account_name,
        secret=service_account_secret))


def delete_service_account(service_account_name: str, service_account_secret: str) -> None:
    """
    Deletes service account and secret with private key that belongs to the
    service account.
    """
    out, err, rc = shakedown.run_dcos_command(
        "security org service-accounts delete {name}".format(name=service_account_name))
    out, err, rc = shakedown.run_dcos_command(
        "security secrets delete {secret}".format(secret=service_account_secret))

    # Files generated by service-accounts keypair command should get removed
    keypair_files = ['private-key.pem', 'public-key.pem']
    for keypair_file in keypair_files:
        try:
            os.unlink(keypair_file)
        except OSError:
            pass


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
    grant_permissions(
        linux_user='root',
        role_name='{}-role'.format(framework_name),
        service_account_name='service-acct'
    )
    grant_permissions(
        linux_user='root',
        role_name='test__integration__{}-role'.format(framework_name),
        service_account_name='service-acct'
    )
    log.info('Finished setting up strict-mode security')


def cleanup_security(framework_name: str) -> None:
    # log.info('Cleaning up strict-mode security')
    revoke_permissions(
        linux_user='root',
        role_name='test__integration__{}-role'.format(framework_name),
        service_account_name='service-acct'
    )
    revoke_permissions(
        linux_user='root',
        role_name='{}-role'.format(framework_name),
        service_account_name='service-acct'
    )
    delete_service_account(service_account_name='service-acct', service_account_secret='secret')
    # log.info('Finished cleaning up strict-mode security')


def security_session(framework_name: str) -> None:
    """Create a service account and configure permissions for strict-mode tests.

    This should generally be used as a fixture in a framework's conftest.py:

    @pytest.fixture(scope='session')
    def configure_security(configure_universe):
        yield from sdk_security.security_session(framework_name)
    """
    try:
        security_mode = os.environ.get('SECURITY')
        if security_mode == 'strict':
            setup_security(framework_name)
        yield
    finally:
        if security_mode == 'strict':
            cleanup_security(framework_name)
