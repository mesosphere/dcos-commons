""" This file configures python logging for the pytest framework
integration tests

Note: pytest must be invoked with this file in the working directory
E.G. py.test frameworks/<your-frameworks>/tests
"""
import logging
import os


log_level = os.getenv('TEST_LOG_LEVEL', 'INFO').upper()

log_levels = (
    'DEBUG',
    'INFO',
    'WARNING',
    'ERROR',
    'CRITICAL',
    'EXCEPTION')

assert log_level in log_levels, '{} is not a valid log level. ' \
    'Use one of: {}'.format(log_level, ', '.join(log_levels))

logging.basicConfig(
    format='[%(asctime)s|%(name)s|%(levelname)s]: %(message)s',
    level=log_level)
