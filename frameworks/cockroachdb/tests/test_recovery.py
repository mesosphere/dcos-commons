import json

import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_tasks as tasks
from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.recovery
def test_pods_restart():
    cockroachdb_ids = tasks.get_task_ids(PACKAGE_NAME, 'cockroachdb-0')

    # get current agent id:
    stdout = cmd.run_cli('cockroachdb pods info cockroachdb-0')
    old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    stdout = cmd.run_cli('cockroachdb pods restart cockroachdb-0')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'cockroachdb-0'
    assert len(jsonobj['tasks']) == 2
    assert jsonobj['tasks'][0] == 'cockroachdb-0-metrics'
    assert jsonobj['tasks'][1] == 'cockroachdb-0-node-init'

    tasks.check_tasks_updated(PACKAGE_NAME, 'cockroachdb', cockroachdb_ids)
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)

    # check agent didn't move:
    stdout = cmd.run_cli('cockroachdb pods info cockroachdb-0')
    new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    assert old_agent == new_agent
