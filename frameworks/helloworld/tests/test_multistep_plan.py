import logging

import pytest
import sdk_install
import sdk_tasks
from tests import config

log = logging.getLogger(__name__)


@pytest.fixture(scope="module", autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {"service": {"yaml": "multistep_plan"}}

        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 1, additional_options=options)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.config_update
def test_bump_hello_cpus():
    def close_enough(val0, val1):
        epsilon = 0.00001
        diff = abs(val0 - val1)
        return diff < epsilon

    config.check_running(config.SERVICE_NAME)
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, "hello")
    log.info("hello ids: " + str(hello_ids))

    updated_cpus = config.bump_hello_cpus(config.SERVICE_NAME)

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, "hello", hello_ids)
    config.check_running(config.SERVICE_NAME)

    all_tasks = sdk_tasks.get_service_tasks(config.SERVICE_NAME, task_prefix='hello')
    running_tasks = [t for t in all_tasks if t.state == "TASK_RUNNING"]
    for t in running_tasks:
        assert close_enough(t.resources['cpus'], updated_cpus)
