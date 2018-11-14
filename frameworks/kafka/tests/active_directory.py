import os

from sdk.testing import sdk_auth


ACTIVE_DIRECTORY_ENVVAR = "TESTING_ACTIVE_DIRECTORY_SERVER"


class ActiveDirectoryKerberos(sdk_auth.KerberosEnvironment):
    def __init__(self):
        self.ad_server = os.environ.get(ACTIVE_DIRECTORY_ENVVAR)

    def get_host(self):
        return self.ad_server

    @staticmethod
    def get_port():
        return 88

    @staticmethod
    def get_realm():
        return "AD.MESOSPHERE.COM"

    @staticmethod
    def get_keytab_path():
        return "__dcos_base64__kafka_ad_keytab"

    @staticmethod
    def cleanup():
        pass


def is_active_directory_enabled():
    return ACTIVE_DIRECTORY_ENVVAR in os.environ
