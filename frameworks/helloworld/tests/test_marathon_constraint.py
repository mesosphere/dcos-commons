import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan
import sdk_tasks as tasks
import sdk_marathon as marathon
import sdk_spin as spin
import time

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
    plan.get_deployment_plan(PACKAGE_NAME)

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
    plan.get_deployment_plan(PACKAGE_NAME)
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
    plan.get_deployment_plan(PACKAGE_NAME)
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
    plan.get_deployment_plan(PACKAGE_NAME)
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
@pytest.mark.placement
def test_change_constraint_increase_count():

    install.uninstall(PACKAGE_NAME)

    some_agent = shakedown.get_private_agents().pop()
    hello_count = 3 if num_private_agents > 3 else  num_private_agents

    options = {
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": hello_count - 1,
            "placement": "hostname:UNIQUE"
        },
        "world": {
            "count": 0
        }
    }

    # hostname:CLUSTER:{} will produce:
    #    "placement-rule": {
    #            "@type": "HostnameRule",
    #            "matcher": {
    #              "@type": "ExactMatcher",
    #              "string": "10.0.0.51"
    #            }

    install.install(PACKAGE_NAME, hello_count - 1, additional_options=options)
    plan.get_deployment_plan(PACKAGE_NAME)
    service_plan_wait()

    tasks.check_running(PACKAGE_NAME, hello_count - 1)
    ensure_multiple_per_agent(hello=1, world=0)

    # change placement constraint, but tasks should not update
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')
    config = marathon.get_config(PACKAGE_NAME)

    # 1) Increase count and change placement rule

    config['env']['HELLO_COUNT'] = str(hello_count)
    config['env']['HELLO_PLACEMENT'] = 'hostname:CLUSTER:{}'.format(some_agent)
    marathon.update_app(PACKAGE_NAME, config)
    plan.get_deployment_plan(PACKAGE_NAME)
    service_plan_wait()

    tasks.check_running(PACKAGE_NAME, hello_count)

    # if config update does not ignore placement rule, it should fail
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'hello', hello_ids)

    # 2) Replace old tasks

    # all tasks should end up on `some_agent`, even the one that started there
    for pod_index in range(hello_count - 1):
        cmd.run_cli('hello-world pods replace hello-{}'.format(pod_index))
        # wait a little so tasks can be restarted
        time.sleep(30)
        # wait till replace completes
        tasks.check_running(PACKAGE_NAME, hello_count)

    ensure_multiple_per_agent(hello=hello_count, world=0)


@pytest.mark.sanity
@pytest.mark.recovery
@pytest.mark.placement
def test_change_constraint_no_move():

    install.uninstall(PACKAGE_NAME)

    some_agent = shakedown.get_private_agents().pop()
    hello_count = 3 if num_private_agents > 3 else  num_private_agents

    options = {
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": hello_count,
            "placement": 'hostname:CLUSTER:{}'.format(some_agent)
        },
        "world": {
            "count": 0
        }
    }

    install.install(PACKAGE_NAME, hello_count, additional_options=options)
    plan.get_deployment_plan(PACKAGE_NAME)
    service_plan_wait()

    tasks.check_running(PACKAGE_NAME, hello_count)
    ensure_multiple_per_agent(hello=hello_count, world=0)

    # 1) Change placement rule
    config = marathon.get_config(PACKAGE_NAME)
    config['env']['HELLO_PLACEMENT'] = 'hostname:UNIQUE'
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')
    marathon.update_app(PACKAGE_NAME, config)
    plan.get_deployment_plan(PACKAGE_NAME)
    service_plan_wait()


    # tasks should not restart and should not change agent
    ensure_multiple_per_agent(hello=hello_count, world=0)
    tasks.check_running(PACKAGE_NAME, hello_count)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'hello', hello_ids)

    # 2) Restart all nodes

    for pod_index in range(hello_count):
            cmd.run_cli('hello-world pods restart hello-{}'.format(pod_index))
            # wait a little so tasks can be restarted
            time.sleep(30)
            tasks.check_running(PACKAGE_NAME, hello_count)

    # tasks should not change agent
    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', hello_ids)
    ensure_multiple_per_agent(hello=hello_count, world=0)


@pytest.mark.sanity
@pytest.mark.recovery
@pytest.mark.placement1
def test_change_constraint_replace():

    install.uninstall(PACKAGE_NAME)

    some_agent = shakedown.get_private_agents().pop()
    hello_count = 3 if num_private_agents > 3 else  num_private_agents

    options = {
        "service": {
            "spec_file": "examples/marathon_constraint.yml"
        },
        "hello": {
            "count": hello_count,
            "placement": 'hostname:CLUSTER:{}'.format(some_agent)
        },
        "world": {
            "count": 0
        }
    }
       
    install.install(PACKAGE_NAME, hello_count, additional_options=options)
    plan.get_deployment_plan(PACKAGE_NAME)
    service_plan_wait()

    tasks.check_running(PACKAGE_NAME, hello_count)
    ensure_multiple_per_agent(hello=hello_count, world=0)

    # 1) Replace all tasks

    config = marathon.get_config(PACKAGE_NAME)
    config['env']['HELLO_PLACEMENT'] = 'hostname:UNIQUE'
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')
    marathon.update_app(PACKAGE_NAME, config)
    plan.get_deployment_plan(PACKAGE_NAME)
    service_plan_wait()

    for pod_index in range(hello_count):
        cmd.run_cli('hello-world pods replace hello-{}'.format(pod_index))
        # wait a little so tasks can be replaced
        time.sleep(30)
        # wait till replace completes
        tasks.check_running(PACKAGE_NAME, hello_count)

    # tasks restarted and changed nodes
    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', hello_ids)
    ensure_multiple_per_agent(hello=1, world=0)

    # 2) Invalid placement

    config['env']['HELLO_PLACEMENT'] = 'hostname:CLUSTER:dontexist'
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')
    marathon.update_app(PACKAGE_NAME, config)
    plan.get_deployment_plan(PACKAGE_NAME)
    service_plan_wait()

    # tasks should not restart and should not change agent
    ensure_multiple_per_agent(hello=1, world=0)
    tasks.check_running(PACKAGE_NAME, hello_count)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'hello', hello_ids)

    for pod_index in range(hello_count):
        cmd.run_cli('hello-world pods restart hello-{}'.format(pod_index))
        # wait a little so tasks can be restarted
        time.sleep(30)        
        tasks.check_running(PACKAGE_NAME, hello_count)

    ensure_multiple_per_agent(hello=1, world=0)
    tasks.check_running(PACKAGE_NAME, hello_count)
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'hello', hello_ids)


# From Kafka tests
def service_plan_wait():
    def fun():
        try:
            return cmd.run_cli('hello-world plan show deploy')
        except:
            return False

    return spin.time_wait_return(fun)


