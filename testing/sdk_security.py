"""
************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_security IN ANY OTHER PARTNER REPOS
************************************************************************
"""
import logging
import os
import retrying

from subprocess import check_output

from typing import Any, Dict, Iterator, List, Set

import sdk_cmd
import sdk_utils

log = logging.getLogger(__name__)


DEFAULT_LINUX_USER = "nobody"


def install_enterprise_cli(force: bool = False) -> None:
    """ Install the enterprise CLI if required """

    log.info("Installing DC/OS enterprise CLI")
    if not force:
        _, stdout, _ = sdk_cmd.run_cli("security --version", print_output=False)
        if stdout:
            log.info("DC/OS enterprise version %s CLI already installed", stdout.strip())
            return

    @retrying.retry(
        stop_max_attempt_number=3,
        wait_fixed=2000,
        retry_on_exception=lambda e: isinstance(e, Exception),
    )
    def _install_impl() -> None:
        sdk_cmd.run_cli("package install --yes --cli dcos-enterprise-cli", check=True)

    _install_impl()


def _grant(user: str, acl: str, description: str, action: str) -> None:
    log.info(
        "Granting permission to {user} for {acl}/{action} ({description})".format(
            user=user, acl=acl, action=action, description=description
        )
    )

    # Create the ACL
    r = sdk_cmd.cluster_request(
        "PUT",
        "/acs/api/v1/acls/{acl}".format(acl=acl),
        raise_on_error=False,
        json={"description": description},
    )
    # 201=created, 409=already exists
    assert r.status_code in [201, 409], "{} failed {}: {}".format(r.url, r.status_code, r.text)

    # Assign the user to the ACL
    r = sdk_cmd.cluster_request(
        "PUT",
        "/acs/api/v1/acls/{acl}/users/{user}/{action}".format(acl=acl, user=user, action=action),
        raise_on_error=False,
    )
    # 204=success, 409=already exists
    assert r.status_code in [204, 409], "{} failed {}: {}".format(r.url, r.status_code, r.text)


def _revoke(user: str, acl: str, description: str, action: str) -> None:
    # TODO(kwood): INFINITY-2065 - implement security cleanup
    log.info("Want to delete {user}+{acl}".format(user=user, acl=acl))


def get_default_permissions(
    service_account_name: str, role: str, linux_user: str,
) -> List[Dict[str, str]]:
    return [
        # registration permissions
        {
            "user": service_account_name,
            "acl": "dcos:mesos:master:framework:role:{}".format(role),
            "description": "Service {} may register with the Mesos master with role={}".format(
                service_account_name, role
            ),
            "action": "create",
        },
        # task execution permissions
        {
            "user": service_account_name,
            "acl": "dcos:mesos:master:task:user:{}".format(linux_user),
            "description": "Service {} may execute Mesos tasks as user={}".format(
                service_account_name, linux_user
            ),
            "action": "create",
        },
        # XXX 1.10 currently requires this mesos:agent permission as well as
        # mesos:task permission.  unclear if this will be ongoing requirement.
        # See DCOS-15682
        {
            "user": service_account_name,
            "acl": "dcos:mesos:agent:task:user:{}".format(linux_user),
            "description": "Service {} may execute Mesos tasks as user={}".format(
                service_account_name, linux_user
            ),
            "action": "create",
        },
        # resource permissions
        {
            "user": service_account_name,
            "acl": "dcos:mesos:master:reservation:role:{}".format(role),
            "description": "Service {} may reserve Mesos resources with role={}".format(
                service_account_name, role
            ),
            "action": "create",
        },
        {
            "user": service_account_name,
            "acl": "dcos:mesos:master:reservation:principal:{}".format(service_account_name),
            "description": "Service {} may reserve Mesos resources with principal={}".format(
                service_account_name, service_account_name
            ),
            "action": "delete",
        },
        # volume permissions
        {
            "user": service_account_name,
            "acl": "dcos:mesos:master:volume:role:{}".format(role),
            "description": "Service {} may create Mesos volumes with role={}".format(
                service_account_name, role
            ),
            "action": "create",
        },
        {
            "user": service_account_name,
            "acl": "dcos:mesos:master:volume:principal:{}".format(service_account_name),
            "description": "Service {} may create Mesos volumes with principal={}".format(
                service_account_name, service_account_name
            ),
            "action": "delete",
        },
    ]


def grant_permissions(
    linux_user: str,
    role_name: str,
    service_account_name: str,
    permissions: List[Dict[str, str]],
) -> List[Dict[str, str]]:
    log.info("Granting permissions to {account}".format(account=service_account_name))

    if not permissions:
        permissions = get_default_permissions(service_account_name, role_name, linux_user)

    for permission in permissions:
        _grant(
            permission["user"], permission["acl"], permission["description"], permission["action"]
        )
    log.info("Permission setup completed for {account}".format(account=service_account_name))

    return permissions


def revoke_permissions(
    service_account_name: str, role_name: str, permissions: List[Dict[str, str]]
) -> None:
    log.info("Revoking permissions from %s (role: %s)", service_account_name, role_name)

    for permission in permissions:
        _revoke(
            permission["user"], permission["acl"], permission["description"], permission["action"]
        )
    log.info("Permission cleanup completed for %s (role: %s)", service_account_name, role_name)


def create_service_account(
    service_account_name: str, service_account_secret: str
) -> None:
    """
    Creates a service account. If it already exists, it is deleted.
    """
    install_enterprise_cli()

    if "/" in service_account_name:
        log.error("The specified service account name (%s) contains /'s", service_account_name)
        raise Exception("A service account must not contain slashes")

    log.info(
        "Creating service account for account={account} secret={secret}".format(
            account=service_account_name, secret=service_account_secret
        )
    )

    log.info("Remove any existing service account and/or secret")
    delete_service_account(service_account_name, service_account_secret)

    log.info("Create keypair")
    sdk_cmd.run_cli("security org service-accounts keypair private-key.pem public-key.pem")

    log.info("Create service account")
    sdk_cmd.run_cli(
        "security org service-accounts create -p public-key.pem "
        '-d "Service account for integration tests" "{account}"'.format(
            account=service_account_name
        )
    )

    log.info("Create secret")
    sdk_cmd.run_cli(
        'security secrets create-sa-secret --strict private-key.pem "{account}" "{secret}"'.format(
            account=service_account_name, secret=service_account_secret
        )
    )

    log.info(
        "Service account created for account={account} secret={secret}".format(
            account=service_account_name, secret=service_account_secret
        )
    )


def delete_service_account(service_account_name: str, service_account_secret: str) -> None:
    """
    Deletes service account with private key that belongs to the service account.
    """
    # ignore any failures:
    sdk_cmd.run_cli("security org service-accounts delete {name}".format(name=service_account_name))

    # Files generated by service-accounts keypair command should get removed
    for keypair_file in ["private-key.pem", "public-key.pem"]:
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


def _get_service_role(service_name: str):
    # TODO: spark_utils uses:
    # app_id_encoded = urllib.parse.quote(
    #     urllib.parse.quote(app_id, safe=''),
    #     safe=''
    # )
    # We'll include the following roles
    # 1. Service name based role. (always included)
    # 2. slave_public (if no top level group exists)
    # 3. Quota based role (if top level group exists)

    # Final list of roles.
    roles_list = []

    # Role based on service name (legacy compatibility)
    role_basename = service_name.strip("/").replace("/", "__")
    service_name_role = "{}-role".format(role_basename)
    roles_list.append(service_name_role)
    # Refined role for slave_public
    roles_list.append("slave_public%252F{}-role".format(role_basename))

    # Find if we have a top-level group role.
    if service_name.partition("/")[1]:
        # Add top level group role.
        roles_list.append(service_name.partition("/")[0])
    else:
        # No top level groups exist, add default slave_public role.
        roles_list.append("slave_public")

    return roles_list


def _get_integration_test_foldered_role(service_name: str) -> List[str]:
    """
    The following role is required due to how the test fixtures are used.
    """

    role_basename = service_name.strip("/").replace("/", "__")
    return ["test__integration__{}-role".format(role_basename)]


def setup_security(
    service_name: str,
    roles: List[str] = [],
    permissions: List[Dict[str, str]] = [],
    linux_user: str = DEFAULT_LINUX_USER,
    service_account: str = "service-acct",
    service_account_secret: str = "secret",
) -> Dict[str, Any]:

    create_service_account(
        service_account_name=service_account, service_account_secret=service_account_secret
    )

    security_info: Dict[str, Any] = {
        "name": service_account,
        "secret": service_account_secret,
        "linux_user": linux_user,
        "roles": [],
        "permissions": {},
        "is_strict": sdk_utils.is_strict_mode(),
    }

    if not security_info["is_strict"]:
        log.info("Skipping strict-mode security setup on non-strict cluster")
        return security_info

    log.info("Setting up strict-mode security")

    security_info["roles"] = roles.copy() if roles else _get_service_role(service_name)

    for role_name in security_info["roles"]:
        security_info["permissions"][role_name] = grant_permissions(
            linux_user=linux_user,
            role_name=role_name,
            service_account_name=service_account,
            permissions=permissions,
        )

    log.info("Finished setting up strict-mode security")

    return security_info


def cleanup_security(service_name: str, security_info: Dict[str, Any]) -> None:

    service_account = security_info.get("name", "service-acct")
    service_account_secret = security_info.get("secret", "secret")

    log.info("Cleaning up strict-mode security")

    for role_name, permissions in security_info["permissions"].items():
        revoke_permissions(service_account, role_name, permissions)

    delete_service_account(service_account, service_account_secret)

    log.info("Finished cleaning up strict-mode security")


def security_session(
    framework_name: str,
    permissions: List[Dict[str, str]] = [],
    linux_user: str = DEFAULT_LINUX_USER,
    service_account: str = "service-acct",
    service_account_secret: str = "secret",
) -> Iterator[None]:
    """Create a service account and configure permissions for strict-mode tests.

    This should generally be used as a fixture in a framework's conftest.py:

    @pytest.fixture(scope='session')
    def configure_security(configure_universe):
        yield from sdk_security.security_session(framework_name, permissions, linux_user, 'service-acct')
    """
    is_strict = sdk_utils.is_strict_mode()
    try:
        if is_strict:
            roles = _get_service_role(framework_name) + _get_integration_test_foldered_role(
                framework_name
            )
            security_info = setup_security(
                framework_name,
                roles=roles,
                permissions=permissions,
                linux_user=linux_user,
                service_account=service_account,
                service_account_secret=service_account_secret,
            )
        yield
    finally:
        if is_strict:
            cleanup_security(framework_name, security_info)


def openssl_ciphers() -> Set[str]:
    return set(
        check_output(["openssl", "ciphers", "ALL:eNULL"]).decode("utf-8").rstrip().split(":")
    )


def is_cipher_enabled(
    service_name: str, task_name: str, cipher: str, endpoint: str, openssl_timeout: str = "1"
) -> bool:
    @retrying.retry(
        stop_max_attempt_number=3,
        wait_fixed=2000,
        retry_on_result=lambda result: "Failed to enter mount namespace" in result,
    )
    def run_openssl_command() -> str:
        command = " ".join(
            [
                "timeout",
                openssl_timeout,
                "openssl",
                "s_client",
                "-cipher",
                cipher,
                "-connect",
                endpoint,
            ]
        )

        _, stdout, stderr = sdk_cmd.service_task_exec(service_name, task_name, command)
        return stdout + "\n" + stderr

    output = run_openssl_command()

    return "Cipher is {}".format(cipher) in output
