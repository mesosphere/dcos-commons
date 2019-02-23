import pytest
import sdk_security
from tests import config


@pytest.fixture(scope="session")
def configure_security(configure_universe):
    yield from sdk_security.security_session(config.SERVICE_NAME)


# pytest fixtures to run hello-world scale test located in frameworks/helloworld/tests/scale
def pytest_addoption(parser):
    parser.addoption('--count', action='store', default=1, type=int,
                     help='Number of hello world services to deploy with a given scenario')
    parser.addoption("--scenario", action='store', default='',
                     help="hello world service yml to use")
    parser.addoption("--service-name", action='store', default='hello-world',
                     help="custom service name to be used instead of default 'hello-world'")
    parser.addoption('--min', action='store', default=-1,
                     help='min jenkins index to start from (default: -1).')
    parser.addoption('--max', action='store', default=-1,
                     help='max jenkins index to end at (default: -1).')
    parser.addoption('--batch-size', action='store', default=1,
                     help='batch size to deploy jenkins masters in (default: 1).')


@pytest.fixture
def service_count(request) -> int:
    return int(request.config.getoption('--count'))


@pytest.fixture
def scenario(request) -> str:
    return str(request.config.getoption('--scenario'))


@pytest.fixture
def service_name(request) -> str:
    return str(request.config.getoption('--service-name'))


@pytest.fixture
def min_index(request) -> int:
    return int(request.config.getoption('--min'))


@pytest.fixture
def max_index(request) -> int:
    return int(request.config.getoption('--max'))


@pytest.fixture
def batch_size(request) -> int:
    return int(request.config.getoption('--batch-size'))
