import pytest
import sdk_repository as repo
import sdk_utils

@pytest.fixture(scope='session')
def configure_universe():
    stub_urls = {}
    try:
        sdk_utils.out("Adding stub universe URL")
        stub_urls = repo.add_universe_repos()
        yield # let the test session execute
    finally:
        repo.remove_universe_repos(stub_urls)
