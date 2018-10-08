import pytest

import sdk_utils
import sdk_auth
import sdk_install

from security import transport_encryption

from tests import config, auth

pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"),
        reason="Kerberos tests require DC/OS 1.10 or higher",
    ),
]


@pytest.fixture(scope="module", autouse=True)
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        name = config.SERVICE_NAME
        service_account_info = transport_encryption.setup_service_account(name)
        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(name, service_account_info)


@pytest.fixture(scope="module", autouse=True)
def kerberos(configure_security):
    try:
        principals = auth.get_service_principals(config.SERVICE_NAME, sdk_auth.REALM)

        kerberos_env = sdk_auth.KerberosEnvironment()
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.mark.auth
@pytest.mark.sanity
def test_install_without_additional_principal_to_user_mapping(kerberos, service_account):
    try:
        service_options = {
            "service": {
                "name": config.SERVICE_NAME,
                "service_account": service_account["name"],
                "service_account_secret": service_account["secret"],
                "security": {
                    "kerberos": {
                        "enabled": True,
                        "debug": True,
                        "kdc": {"hostname": kerberos.get_host(), "port": int(kerberos.get_port())},
                        "realm": kerberos.get_realm(),
                        "keytab_secret": kerberos.get_keytab_path(),
                    }
                },
            }
        }

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=service_options,
            timeout_seconds=30 * 60,
        )
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
