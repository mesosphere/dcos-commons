import functools
import retrying

DEFAULT_RETRY_WAIT_MS = 1000
DEFAULT_RETRY_MAX_ATTEMPTS = 5


def retry(fn):
    @functools.wraps(fn)
    @retrying.retry(
        wait_fixed=DEFAULT_RETRY_WAIT_MS, stop_max_attempt_number=DEFAULT_RETRY_MAX_ATTEMPTS
    )
    def retried_fn(*args, **kwargs):
        return fn(*args, **kwargs)

    return retried_fn
