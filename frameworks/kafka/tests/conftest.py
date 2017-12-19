import pytest
import sdk_plan
import sdk_repository
import sdk_security
import sdk_utils


# allow overriding these names via envvars, for confluent tests:
PACKAGE_NAME = sdk_utils.get_package_name("beta-kafka")
SERVICE_NAME = sdk_utils.get_service_name(PACKAGE_NAME.lstrip("beta-"))


@pytest.fixture(scope='session')
def configure_universe():
    yield from sdk_repository.universe_session()


@pytest.fixture(scope='session')
def configure_security(configure_universe):
    yield from sdk_security.security_session(SERVICE_NAME)


@pytest.fixture(autouse=True)
def get_plans_on_failure(request):
    yield from sdk_plan.log_plans_if_failed(SERVICE_NAME, request)
