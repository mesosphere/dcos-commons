import pytest
import sdk_plan
import sdk_repository
import sdk_security
from tests import config


@pytest.fixture(scope='session')
def configure_universe():
    yield from sdk_repository.universe_session()


@pytest.fixture(scope='session')
def configure_security(configure_universe):
    yield from sdk_security.security_session(config.SERVICE_NAME)


@pytest.fixture(autouse=True)
def get_plans_on_failure(request):
    yield from sdk_plan.log_plans_if_failed(config.SERVICE_NAME, request)
