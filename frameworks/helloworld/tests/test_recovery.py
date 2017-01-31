import json
import pytest
import shakedown
import time

import sdk_cmd as cmd
import sdk_install as install
import sdk_tasks as tasks

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    check_running
)


def setup_module():
    install.uninstall(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.recovery
def test_kill_hello_node():
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello-0')
    tasks.kill_task_with_pattern('hello', 'hello-0-server.hello-world.mesos')
    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', hello_ids)

    check_running()


@pytest.mark.sanity
@pytest.mark.recovery
def test_pods_restart():
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello-0')

    # get current agent id:
    stdout = cmd.run_cli('hello-world pods info hello-0')
    old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    stdout = cmd.run_cli('hello-world pods restart hello-0')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'hello-0-server'

    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', hello_ids)
    check_running()

    # check agent didn't move:
    stdout = cmd.run_cli('hello-world pods info hello-0')
    new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    assert old_agent == new_agent


@pytest.mark.sanity
@pytest.mark.recovery
def test_pods_replace():
    world_ids = tasks.get_task_ids(PACKAGE_NAME, 'world-0')

    # get current agent id:
    stdout = cmd.run_cli('hello-world pods info world-0')
    old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    jsonobj = json.loads(cmd.run_cli('hello-world pods replace world-0'))
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'world-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'world-0-server'

    tasks.check_tasks_updated(PACKAGE_NAME, 'world-0', world_ids)
    check_running()

    # check agent moved:
    stdout = cmd.run_cli('hello-world pods info world-0')
    new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    # TODO: enable assert if/when agent is guaranteed to change (may randomly move back to old agent)
    #assert old_agent != new_agent
