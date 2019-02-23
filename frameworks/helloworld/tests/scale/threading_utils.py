import time
import logging 

from threading import Thread
from typing import List, Set

TIMINGS = {}
DEPLOY_TIMEOUT = 30 * 60  # 30 mins


log = logging.getLogger(__name__)


class ResultThread(Thread):
    """A thread that stores the result of the run command."""

    def __init__(self, *args, **kwargs) -> None:
        super().__init__(*args, **kwargs)
        self._result = None
        self._event = None

    @property
    def result(self) -> bool:
        """Run result

        Returns: True if completed successfully.

        """
        return bool(self._result)

    @property
    def event(self):
        return self._event

    @event.setter
    def event(self, event):
        self._event = event

    def run(self) -> None:
        start = time.time()
        try:
            super().run()
            self._result = True
        except Exception as e:
            self._result = False
            log.error("Exception occured in {} with inner exception: {}", self.name, e)
        finally:
            end = time.time()
            if self.event:
                TIMINGS[self.event][self.name] = end - start


def spawn_threads(names, target, daemon=False, event=None, **kwargs) -> List[ResultThread]:
    """Create and start threads running target. This will pass
    the thread name to the target as the first argument.

    Args:
        names: Thread names
        target: Function to run in thread
        **kwargs: Keyword args for target

    Returns:
        List of threads handling target.
    """
    thread_list = list()
    for service_name in names:
        # setDaemon allows the main thread to exit even if
        # these threads are still running.
        t = ResultThread(target=target,
                         daemon=daemon,
                         name=service_name,
                         args=(service_name,),
                         kwargs=kwargs)
        t.event = event
        thread_list.append(t)
        t.start()
    return thread_list


def wait_on_threads(thread_list: List[Thread], timeout=DEPLOY_TIMEOUT) -> List[Thread]:
    """Wait on the threads in `install_threads` until a specified time
    has elapsed.

    Args:
        thread_list: List of threads
        timeout: Timeout is seconds

    Returns:
        List of threads that are still running.

    """
    start_time = current_time = time.time()
    for thread in thread_list:
        remaining = timeout - (current_time - start_time)
        if remaining < 1:
            break
        thread.join(timeout=remaining)
        current_time = time.time()
    active_threads = [x for x in thread_list if x.isAlive()]
    return active_threads


def wait_and_get_failures(thread_list: List[ResultThread], **kwargs) -> Set[Thread]:
    """Wait on threads to complete or timeout and log errors.

    Args:
        thread_list: List of threads to wait on

    Returns: A list of service names that failed or timed out.

    """
    timeout_failures = wait_on_threads(thread_list, **kwargs)
    timeout_names = [x.name for x in timeout_failures]
    if timeout_names:
        log.warning("The following {:d} instance(s) failed to "
                    "complete in {:d} minutes: {}"
                    .format(len(timeout_names),
                            DEPLOY_TIMEOUT // 60,
                            ', '.join(timeout_names)))
    # the following did not timeout, but failed
    run_failures = [x for x in thread_list if not x.result]
    run_fail_names = [x.name for x in run_failures]
    if run_fail_names:
        log.warning("The following {:d} instance(s) "
                    "encountered an error: {}"
                    .format(len(run_fail_names),
                            ', '.join(run_fail_names)))
    return set(timeout_failures + run_failures)
