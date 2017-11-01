import logging
import pytest
import time

import sdk_auth
import sdk_hosts
import sdk_install
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        primaries = ["hdfs", "HTTP"]
        fqdn = "{service_name}.{host_suffix}".format(
            service_name=config.SERVICE_NAME, host_suffix=sdk_hosts.AUTOIP_HOST_SUFFIX)
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

        kerberos = sdk_auth.KerberosEnvironment()
        kerberos.add_principals(principals)
        kerberos.finalize_environment()
        service_kerberos_options = {
            "service": {
                "kerberos": {
                    "enabled": True,
                    "kdc_host_name": kerberos.get_host(),
                    "kdc_host_port": kerberos.get_port(),
                    "keytab_secret_path": kerberos.get_keytab_path(),
                    "primary": primaries[0],
                    "primary_http": primaries[1],
                    "realm": sdk_auth.REALM
                }
            }
        }
        # TODO: uncomment install when kerberized-HDFS branch is merged
        #sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        #sdk_install.install(
        #    config.PACKAGE_NAME,
        #    config.SERVICE_NAME,
        #    config.DEFAULT_TASK_COUNT,
        #    additional_options=service_kerberos_options,
        #    timeout_seconds=30*60)

        yield  # let test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        kerberos.cleanup()


@pytest.mark.auth
@pytest.mark.sanity
def test_user_can_write():
    pass
