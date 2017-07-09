import pytest
import json

import shakedown

import sdk_tasks as tasks
import sdk_install as install
import sdk_utils as utils

from tests.config import (
    PACKAGE_NAME,
    check_running,
    bump_hello_cpus,
    hello_task_count
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    options = {
        "service": {
            "spec_file": "examples/multistep_plan.yml"
        }
    }

    install.install(PACKAGE_NAME, 1, additional_options=options)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.smoke
@pytest.mark.config_update
@pytest.mark.ben
def test_bump_hello_cpus():
    def close_enough(val0, val1):
        epsilon = 0.00001
        diff = abs(val0 - val1)
        return diff < epsilon

    check_running(PACKAGE_NAME)
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')
    utils.out('hello ids: ' + str(hello_ids))

    updated_cpus = bump_hello_cpus(PACKAGE_NAME)

    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', hello_ids)
    check_running(PACKAGE_NAME)

    all_tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    running_tasks = [t for t in all_tasks if t['name'].startswith('hello') and t['state'] == "TASK_RUNNING"]
    for t in running_tasks:
        assert close_enough(t['resources']['cpus'], updated_cpus)

