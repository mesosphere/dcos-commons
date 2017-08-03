import logging
import os


log_level = os.getenv('TEST_LOG_LEVEL', 'INFO').upper()

assert log_level in (
    'DEBUG',
    'INFO',
    'WARNING',
    'ERROR',
    'CRITICAL',
    'EXCEPTION')

log_format = '[%(asctime)s|%(name)s|%(levelname)s]: %(message)s'
if 'TEAMCITY_VERSION' in os.environ:
    log_format = '[%(name)s|%(levelname)s]: %(message)s'

logging.basicConfig(
    format=log_format,
    level=log_level)
