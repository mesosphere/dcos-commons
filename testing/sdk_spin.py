'''Utilities relating to waiting for an operation to complete'''

import sdk_utils
import shakedown

import time
import traceback

DEFAULT_TIMEOUT=15 * 60

#TODO(nickbp): Upstream into shakedown's time_wait()


def time_wait_return(predicate, timeout_seconds=DEFAULT_TIMEOUT, ignore_exceptions=True):
    '''TODO update callers to invoke shakedown directly'''
    return time_wait_noisy(predicate, timeout_seconds=timeout_seconds, ignore_exceptions=ignore_exceptions)


def time_wait_noisy(predicate, timeout_seconds=DEFAULT_TIMEOUT, ignore_exceptions=True):
    '''TODO update callers to invoke shakedown directly'''
    return shakedown.wait_for(predicate, timeout_seconds=timeout_seconds, ignore_exceptions=ignore_exceptions, noisy=True)
