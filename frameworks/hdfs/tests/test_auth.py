import logging
import os
import pytest
import time

import sdk_auth
import sdk_hosts
import sdk_install
import sdk_marathon
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def kerberos(configure_universe):
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

        kerberos_env = sdk_auth.KerberosEnvironment()
        kerberos_env.add_principals(principals)
        kerberos_env.finalize()
        service_kerberos_options = {
            "service": {
                "kerberos": {
                    "enabled": True,
                    "kdc_host_name": kerberos_env.get_host(),
                    "kdc_host_port": kerberos_env.get_port(),
                    "keytab_secret": kerberos_env.get_keytab_path(),
                    "primary": primaries[0],
                    "primary_http": primaries[1],
                    "realm": sdk_auth.REALM
                }
            }
        }

        # TODO: uncomment install when kerberized-HDFS branch is merged
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            config.DEFAULT_TASK_COUNT,
            additional_options=service_kerberos_options,
            timeout_seconds=30*60)

        yield kerberos_env

    finally:
        #sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        #kerberos_env.cleanup()
        pass

# General process for each test
# 1. Will use a keytab local to the client binary (key will either be a generic one or one made within the test context)
# 2. Launch marathon app referencing said keytab via secret store
# 3. container environment will have kerberos CLI and conf file setup
# 4. task exec to app's container
# 5. within each test, depending on the keytab that's pulled, one should kinit with appropriate principal
# 6. use service client binary to interact with client once authed

@pytest.mark.skipif(os.environ.get("SECURITY") == "strict", reason="auth tests currently broken in strict")
@pytest.mark.auth
@pytest.mark.sanity
def test_user_can_write_and_read(kerberos):
    #try:
    #    client_app_def = config.kerberized_hdfs_client_marathon_app
    #    client_app_def["secrets"]["hdfs_keytab"]["source"] = kerberos.get_keytab_path()
    #    sdk_auth.launch_marathon_app(client_app_def)
    #    sdk_tasks.task_exec(client_app_def["id"], "kinit", ...)
    #    sdk_tasks.task_exec(client_app_def["id"], "./bin/hdfs", <some sub cmd>)
    #finally:
    #    sdk_marathon.destroy_app(client_app_def["id"])
    pass
