import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan
import sdk_tasks as tasks
from tests.config import (
    PACKAGE_NAME
)

num_private_agents = len(shakedown.get_private_agents())


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.recovery
def test_unidirectional_constraint():
    install.uninstall(PACKAGE_NAME)
    options = {
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": 1,
            "placement": "hostname:UNIQUE"
        },
        "world": {
            "count": num_private_agents
        }
    }

    install.install(PACKAGE_NAME, num_total_tasks(), additional_options=options)
    # hello deploys first so Marathon placement constraint is satisfied. One "world" task will end up placed with it.
    plan.get_deployment_plan(PACKAGE_NAME)

    cmd.run_cli('hello-world pods replace hello-0')
    # allow up to 10 seconds for hello-0 to show up as TASK_KILLED
    tasks.check_running(PACKAGE_NAME, num_private_agents, timeout_seconds=10)

    # Now hello node should not be placed anywhere, as there is a "world" task on each private node already
    try:
        # if hello-0 is going to get staged, it'll happen pretty quickly
        shakedown.wait_for_task_property_value(PACKAGE_NAME, 'hello-0-server', 'state', 'TASK_STAGING', timeout_sec=10)
        assert False, "hello-0 should not have been staged"
    except shakedown.TimeoutExpired:
        print("Passed: hello-0 did not get staged")
        pass


@pytest.mark.sanity
@pytest.mark.recovery
def test_no_colocation():
    install.uninstall(PACKAGE_NAME)
    options = {
        "hello": {
            "count": 2,
            "placement": "hostname:UNIQUE"
        },
        "world": {
            "count": num_private_agents - 2,
            "placement": "hostname:UNIQUE"
        }
    }

    install.install(PACKAGE_NAME, num_private_agents, additional_options=options)
    plan.get_deployment_plan(PACKAGE_NAME)
    # check that no two tasks of _any_ type are colocated on the same agent
    all_tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    agents = []
    for task in all_tasks:
        if task['name'].startswith('hello-') or task['name'].startswith('world-'):
            agents.append(task['slave_id'])
        else:
            assert False, "Unknown task: " + task['name']
    assert len(agents) == len(set(agents))


def num_total_tasks():
    return num_private_agents + 1
