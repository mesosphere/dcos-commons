import pytest
from sdk.testing import sdk_security
from tests import config


@pytest.fixture(scope="session")
def configure_security(configure_universe):
    yield from sdk_security.security_session(config.SERVICE_NAME)


def pytest_runtest_makereport(item, call):
    """
    This pytest fixture in connection with `pytest_runtest_setup` add support
    for indicating that a set of tests are "incremental".

    When using @pytest.mark.incremental, tests following a failed test will not
    run but is marked as failed immediately.
    """
    if "incremental" in item.keywords:
        if call.excinfo is not None:
            parent = item.parent
            parent._previousfailed = item


def pytest_runtest_setup(item):
    """
    This pytest fixture in connection with `pytest_runtest_makereport` add support
    for indicating that a set of tests are "incremental".

    When using @pytest.mark.incremental, tests following a failed test will not
    run but is marked as failed immediately.
    """
    if "incremental" in item.keywords:
        previousfailed = getattr(item.parent, "_previousfailed", None)
        if previousfailed is not None:
            pytest.xfail("previous test failed (%s)" % previousfailed.name)
