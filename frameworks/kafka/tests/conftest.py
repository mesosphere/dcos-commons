import pytest
import sdk_login
import sdk_repository
import sdk_security


@pytest.fixture(scope='session')
def configure_login():
    yield from sdk_login.login_session()

@pytest.fixture(scope='session')
def configure_universe(configure_login):
    yield from sdk_repository.universe_session()

@pytest.fixture(scope='session')
def configure_security(configure_universe):
    yield from sdk_security.security_session('kafka')
