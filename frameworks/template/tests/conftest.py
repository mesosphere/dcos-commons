import pytest
import sdk_repository
import sdk_security


@pytest.fixture(scope='session')
def configure_universe():
    yield from sdk_repository.universe_session()

@pytest.fixture(scope='session')
def configure_security(configure_universe):
    yield from sdk_security.security_session('template')
