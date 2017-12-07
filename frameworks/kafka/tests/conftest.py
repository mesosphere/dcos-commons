import pytest

import sdk_repository
import sdk_security
import sdk_utils

PACKAGE_NAME = sdk_utils.get_package_name("beta-kafka")
SERVICE_NAME = sdk_utils.get_service_name(PACKAGE_NAME.lstrip("beta-"))


@pytest.fixture(scope='session')
def configure_universe():
    yield from sdk_repository.universe_session()


@pytest.fixture(scope='session')
def configure_security(configure_universe):
    yield from sdk_security.security_session(SERVICE_NAME)
