import pytest

import sdk_utils
import sdk_auth
import sdk_install
import sdk_upgrade

from security import transport_encryption

from tests import config, auth

pytestmark = [
    sdk_utils.dcos_ee_only,
    pytest.mark.skipif(
        sdk_utils.dcos_version_less_than("1.10"),
        reason="Kerberos tests require DC/OS 1.10 or higher",
    ),
]

foldered_name = config.FOLDERED_SERVICE_NAME


@pytest.fixture(scope="module", autouse=True)
def service_account(configure_security):
    """
    Sets up a service account for use with TLS.
    """
    try:
        name = foldered_name
        service_account_info = transport_encryption.setup_service_account(name)
        yield service_account_info
    finally:
        transport_encryption.cleanup_service_account(name, service_account_info)


@pytest.fixture(scope="module", autouse=True)
def kerberos(configure_security):
    try:
        principals = auth.get_service_principals(foldered_name, sdk_auth.REALM)

        kerberos_env = sdk_auth.KerberosEnvironment()
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()

        yield kerberos_env

    finally:
        kerberos_env.cleanup()


@pytest.mark.auth
@pytest.mark.sanity
def test_install_kerberised_hdfs_service(kerberos, service_account):
    try:
        sdk_upgrade.test_upgrade(
            config.PACKAGE_NAME,
            foldered_name,
            config.DEFAULT_TASK_COUNT,
            from_options={
                "service": {
                    "name": foldered_name,
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
                    }
                }
            },
            timeout_seconds=30 * 60,
        )
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, foldered_name)
