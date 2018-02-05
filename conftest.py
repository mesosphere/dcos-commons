""" This file configures python logging for the pytest framework
integration tests

Note: pytest must be invoked with this file in the working directory
E.G. py.test frameworks/<your-frameworks>/tests
"""
import logging
import os
import os.path
import sys

import pytest
import sdk_diag
import sdk_utils
import teamcity

log_level = os.getenv('TEST_LOG_LEVEL', 'INFO').upper()
log_levels = ('DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL', 'EXCEPTION')
assert log_level in log_levels, \
    '{} is not a valid log level. Use one of: {}'.format(log_level, ', '.join(log_levels))
# write everything to stdout due to the following circumstances:
# - shakedown uses print() aka stdout
# - teamcity splits out stdout vs stderr into separate outputs, we'd want them combined
logging.basicConfig(
    format='[%(asctime)s|%(name)s|%(levelname)s]: %(message)s',
    level=log_level,
    stream=sys.stdout)

# reduce excessive DEBUG/INFO noise produced by some underlying libraries:
for noise_source in [
        'dcos.http',
        'dcos.marathon',
        'dcos.util',
        'paramiko.transport',
        'urllib3.connectionpool']:
    logging.getLogger(noise_source).setLevel('WARNING')

log = logging.getLogger(__name__)

# The following environment variable allows for log collection to be turned off.
# This is useful, for exampl in testing.
INTEGRATION_TEST_LOG_COLLECTION = str(
    os.environ.get('INTEGRATION_TEST_LOG_COLLECTION', "True")
).lower() in ["true", "1"]


@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item: pytest.Item, call):  # _pytest.runner.CallInfo
    '''Hook to run after every test, before any other post-test hooks.
    See also: https://docs.pytest.org/en/latest/example/simple.html\
    #making-test-result-information-available-in-fixtures
    '''

    # Execute all other hooks to obtain the report object.
    outcome = yield

    # Handle failures. Must be done here and not in a fixture in order to
    # properly handle post-yield fixture teardown failures.
    if INTEGRATION_TEST_LOG_COLLECTION:
        sdk_diag.handle_test_report(item, outcome.get_result())
    else:
        print("INTEGRATION_TEST_LOG_COLLECTION==False. Skipping log collection")


def pytest_runtest_teardown(item: pytest.Item):
    '''Hook to run after every test.'''
    # Inject footer at end of test, may be followed by additional teardown.
    # Don't do this when running in teamcity, where it's redundant.
    if not teamcity.is_running_under_teamcity():
        print('''
==========
======= END: {}::{}
=========='''.format(sdk_diag.get_test_suite_name(item), item.name))


def pytest_runtest_setup(item: pytest.Item):
    '''Hook to run before every test.'''
    # Inject header at start of test, following automatic "path/to/test_file.py::test_name":
    # Don't do this when running in teamcity, where it's redundant.
    if not teamcity.is_running_under_teamcity():
        print('''
==========
======= START: {}::{}
=========='''.format(sdk_diag.get_test_suite_name(item), item.name))

    if INTEGRATION_TEST_LOG_COLLECTION:
        sdk_diag.handle_test_setup(item)
    sdk_utils.check_dcos_min_version_mark(item)
