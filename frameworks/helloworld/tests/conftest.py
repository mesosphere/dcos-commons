import pytest
import sdk_repository


@pytest.fixture(scope='session')
def configure_universe():
    stub_urls = {}
    try:
        stub_urls = sdk_repository.add_universe_repos()
        yield # let the test session execute
    finally:
        sdk_repository.remove_universe_repos(stub_urls)
