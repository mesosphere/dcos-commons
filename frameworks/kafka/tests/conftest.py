import pytest
import sdk_repository as repo


@pytest.fixture(scope='session')
def configure_universe():
    stub_urls = {}
    try:
        stub_urls = repo.add_universe_repos()
        yield # let the test session execute
    finally:
        repo.remove_universe_repos(stub_urls)
