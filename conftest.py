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

logging.basicConfig(
    format='[%(asctime)s] %(levelname)s: %(message)s',
    level=log_level)
