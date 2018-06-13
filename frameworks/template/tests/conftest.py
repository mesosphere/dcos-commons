import pytest

import sdk_security

from tests import config

@pytest.fixture(scope="session")
def configure_security(configure_universe):
    service_account_name = "{}-service-account".format(config.SERVICE_NAME)
    service_account_secret = "{}-service-account-secret".format(config.SERVICE_NAME)
    # Add the permissions you want to grant/revoke during the installation
    permissions = []
    yield from sdk_security.security_session(config.SERVICE_NAME,
                                             permissions,
                                             sdk_security.DEFAULT_LINUX_USER,
                                             service_account_name,
                                             service_account_secret)
