import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan
import sdk_tasks as tasks
import sdk_marathon as marathon
import sdk_spin as spin
import time
import json

from tests.config import (
    PACKAGE_NAME
)

num_private_agents = len(shakedown.get_private_agents())


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.recovery
def test_hostname_unique():
    install.uninstall(PACKAGE_NAME)
    options = {
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": num_private_agents,
            "placement": "hostname:UNIQUE"
        },
        "world": {
            "count": num_private_agents,
            "placement": "hostname:UNIQUE"
        }
    }

    install.install(PACKAGE_NAME, num_private_agents * 2, additional_options=options)
    # hello deploys first. One "world" task should end up placed with each "hello" task.
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    # ensure "hello" task can still be placed with "world" task
    cmd.run_cli('hello-world pods replace hello-0')
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2 - 1, timeout_seconds=10)
    tasks.check_running(PACKAGE_NAME, num_private_agents * 2)
    ensure_multiple_per_agent(hello=1, world=1)


@pytest.mark.sanity
@pytest.mark.recovery
def test_max_per_hostname():
    install.uninstall(PACKAGE_NAME)
    options = {
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": num_private_agents * 2,
            "placement": "hostname:MAX_PER:2"
        },
        "world": {
            "count": num_private_agents * 3,
            "placement": "hostname:MAX_PER:3"
        }
    }

    install.install(PACKAGE_NAME, num_private_agents * 5, additional_options=options)
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    ensure_multiple_per_agent(hello=2, world=3)


@pytest.mark.sanity
@pytest.mark.recovery
def test_rr_by_hostname():
    install.uninstall(PACKAGE_NAME)
    options = {
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": num_private_agents * 2,
            "placement": "hostname:GROUP_BY:{}".format(num_private_agents)
        },
        "world": {
            "count": num_private_agents * 2,
            "placement": "hostname:GROUP_BY:{}".format(num_private_agents)
        }
    }

    install.install(PACKAGE_NAME, num_private_agents * 4, additional_options=options)
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    ensure_multiple_per_agent(hello=2, world=2)


@pytest.mark.sanity
@pytest.mark.recovery
def test_cluster():
    install.uninstall(PACKAGE_NAME)
    some_agent = shakedown.get_private_agents().pop()
    options = {
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": num_private_agents,
            "placement": "hostname:CLUSTER:{}".format(some_agent)
        },
        "world": {
            "count": 0
        }
    }

    install.install(PACKAGE_NAME, num_private_agents, additional_options=options)
    plan.wait_for_completed_deployment(PACKAGE_NAME)
    ensure_multiple_per_agent(hello=num_private_agents, world=0)


def ensure_multiple_per_agent(hello, world):
    all_tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    hello_agents = []
    world_agents = []
    for task in all_tasks:
        if task['name'].startswith('hello-'):
            hello_agents.append(task['slave_id'])
        elif task['name'].startswith('world-'):
            world_agents.append(task['slave_id'])
        else:
            assert False, "Unknown task: " + task['name']
    assert len(hello_agents) == len(set(hello_agents)) * hello
    assert len(world_agents) == len(set(world_agents)) * world


@pytest.mark.sanity
@pytest.mark.recovery
def test_updated_placement_constraints_not_applied_with_other_changes():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    # Additionally, modify the task count to be higher.
    config = marathon.get_config(PACKAGE_NAME)
    config['env']['HELLO_COUNT'] = '2'
    marathon.update_app(PACKAGE_NAME, config)
    service_plan_wait()

    # Now, an additional hello-server task will launch
    # where the _new_ constraint will tell it to be.
    tasks.check_running(PACKAGE_NAME, 2)

    assert get_task_host('hello-0-server') == some_agent
    assert get_task_host('hello-1-server') == other_agent


@pytest.mark.sanity
@pytest.mark.recovery
def test_updated_placement_constraints_no_task_change():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    tasks.check_tasks_not_updated(PACKAGE_NAME, 'hello', old_ids)

    assert get_task_host('hello-0-server') == some_agent


@pytest.mark.sanity
@pytest.mark.recovery
def test_updated_placement_constraints_restarted_tasks_dont_move():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    # Restart the task, and verify it doesn't move hosts
    cmd.run_cli('hello-world pods restart hello-0')
    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', old_ids)

    assert get_task_host('hello-0-server') == some_agent


@pytest.mark.sanity
@pytest.mark.recovery
def test_updated_placement_constraints_replaced_tasks_do_move():
    some_agent, other_agent, old_ids = setup_constraint_switch()

    # Replace the task, and verify it moves hosts
    cmd.run_cli('hello-world pods replace hello-0')
    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', old_ids)

    assert get_task_host('hello-0-server') == other_agent


def setup_constraint_switch():
    install.uninstall(PACKAGE_NAME)

    agents = shakedown.get_private_agents()
    some_agent = agents[0]
    other_agent = agents[1]
    print("agents", some_agent, other_agent)
    assert some_agent != other_agent
    options = {
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": 1,
            # First, we stick the pod to some_agent
            "placement": 'hostname:LIKE:{}'.format(some_agent)
        },
        "world": {
            "count": 0
        }
    }
    install.install(PACKAGE_NAME, 1, additional_options=options)
    tasks.check_running(PACKAGE_NAME, 1)
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')

    # Now, stick it to other_agent
    config = marathon.get_config(PACKAGE_NAME)
    config['env']['HELLO_PLACEMENT'] = 'hostname:LIKE:{}'.format(other_agent)
    marathon.update_app(PACKAGE_NAME, config)
    # Wait for the scheduler to be up before advancing.
    service_plan_wait()

    return some_agent, other_agent, hello_ids


def get_task_host(task_name):
    out = cmd.run_cli('task {} --json'.format(task_name))
    
    for label in json.loads(out)[0]['labels']:
        if label['key'] == 'offer_hostname':
            return label['value']

    raise Exception("offer_hostname label is not present!")


def service_plan_wait():
    def fun():
        try:
            return cmd.run_cli('hello-world plan show deploy')
        except:
            return False

    return spin.time_wait_return(fun)