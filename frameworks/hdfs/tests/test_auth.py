import logging
import pytest
import time

import sdk_auth
import sdk_install
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package():
    try:
        primaries = ["hdfs", "HTTP"]
        fqdn = "{}.autoip.dcos.thisdcos.directory".format(config.SERVICE_NAME)
        instances = [
            "name-0-node",
            "name-0-zkfc",
            "name-1-node",
            "name-1-zkfc",
            "journal-0-node",
            "journal-1-node",
            "journal-2-node",
            "data-0-node",
            "data-1-node",
            "data-2-node",
        ]
        principals = []
        for instance in instances:
            for primary in primaries:
                principals.append(
                    "{primary}/{instance}.{fqdn}@{REALM}".format(
                        primary=primary,
                        instance=instance,
                        fqdn=fqdn,
                        REALM=sdk_auth.REALM
                    )
                )

        kerberos = sdk_auth.KerberosEnvironment(config.SERVICE_NAME)
        kerberos.add_principals(principals)
        kerberos.finalize_environment()
        service_kerberos_options = {
            "service": {
                "kerberos": {
                    "enabled": True,
                    "kdc_address": kerberos.get_address(),
                    "keytab_secret_path": kerberos.get_keytab_path(),
                    "primary": primaries[0],
                    "primary_http": primaries[1],
                    "realm": sdk_auth.REALM
                }
            }
        }
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=service_kerberos_options,
            timeout_seconds=30*60)

        yield # let test session execute
    finally:
        pass
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        kerberos.cleanup()


@pytest.mark.auth
@pytest.mark.sanity
def test_user_can_write():
    pass