import pytest
import sdk_security
from tests import config
from typing import List


@pytest.fixture(scope='session')
def configure_security(configure_universe):
    service_account_name = 'service-acct'
    # Add the permissions you want to grant/revoke during the installation
    permissions = []
    yield from sdk_security.security_session(config.SERVICE_NAME, permissions, config.DEFAULT_LINUX_USER, service_account_name)
