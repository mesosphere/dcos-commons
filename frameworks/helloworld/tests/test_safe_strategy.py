import json

import pytest
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_plan as plan
import sdk_tasks as tasks
import sdk_utils as utils
from tests.config import (
    PACKAGE_NAME
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()
    # due to safe strategy: no tasks should launch, and suppressed shouldn't be set
    install.install(
        PACKAGE_NAME,
        0,
        additional_options={
            'service': {'spec_file': 'examples/safe.yml'},
            'hello': {'count': 3},
            'world': {'count': 1}
        },
        check_suppression=False)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.smoke
@pytest.mark.sanity
def test_safe_init():
    def fn():
        return cmd.run_cli('hello-world pods list')

    assert json.loads(shakedown.wait_for(fn, noisy=True)) == []

    pl = plan.wait_for_plan_status(PACKAGE_NAME, 'deploy', 'WAITING')
    utils.out(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 3
    assert steps[0]['status'] == 'WAITING'
    assert steps[1]['status'] == 'WAITING'
    assert steps[2]['status'] == 'WAITING'

    phase = pl['phases'][1]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 1
    assert steps[0]['status'] == 'WAITING'


@pytest.mark.smoke
@pytest.mark.sanity
def test_safe_first():
    cmd.run_cli('hello-world plan continue deploy hello-deploy')

    expected_tasks = ['hello-0']
    tasks.check_running(PACKAGE_NAME, len(expected_tasks))
    assert json.loads(cmd.run_cli('hello-world pods list')) == expected_tasks

    # do not use service_plan always
    # when here, plan should always return properly
    pl = plan.wait_for_completed_step(PACKAGE_NAME, 'deploy', 'hello-deploy', 'hello-0:[server]')
    utils.out(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 3
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'WAITING'
    assert steps[2]['status'] == 'WAITING'

    phase = pl['phases'][1]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 1
    assert steps[0]['status'] == 'WAITING'


@pytest.mark.smoke
@pytest.mark.sanity
def test_safe_second():
    cmd.run_cli('hello-world plan continue deploy hello-deploy')

    expected_tasks = ['hello-0', 'hello-1']
    tasks.check_running(PACKAGE_NAME, len(expected_tasks))
    assert json.loads(cmd.run_cli('hello-world pods list')) == expected_tasks

    pl = plan.wait_for_completed_step(PACKAGE_NAME, 'deploy', 'hello-deploy', 'hello-1:[server]')
    utils.out(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 3
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'WAITING'

    phase = pl['phases'][1]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 1
    assert steps[0]['status'] == 'WAITING'


@pytest.mark.smoke
@pytest.mark.sanity
def test_safe_third():
    cmd.run_cli('hello-world plan continue deploy hello-deploy')

    expected_tasks = ['hello-0', 'hello-1', 'hello-2']
    tasks.check_running(PACKAGE_NAME, len(expected_tasks))
    assert json.loads(cmd.run_cli('hello-world pods list')) == expected_tasks

    pl = plan.wait_for_completed_phase(PACKAGE_NAME, 'deploy', 'hello-deploy')
    utils.out("wait_for_completed_phase(PACKAGE_NAME, 'deploy', 'hello-deploy') output:")
    utils.out(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 3
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'

    phase = pl['phases'][1]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 1
    assert steps[0]['status'] == 'WAITING'


@pytest.mark.smoke
@pytest.mark.sanity
def test_safe_fourth():
    cmd.run_cli('hello-world plan continue deploy world-deploy')

    expected_tasks = ['hello-0', 'hello-1', 'hello-2',
                      'world-0']
    tasks.check_running(PACKAGE_NAME, len(expected_tasks))
    assert json.loads(cmd.run_cli('hello-world pods list')) == expected_tasks

    pl = plan.wait_for_completed_plan(PACKAGE_NAME, 'deploy')
    utils.out(pl)

    assert pl['status'] == 'COMPLETE'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 3
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'

    phase = pl['phases'][1]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 1
    assert steps[0]['status'] == 'COMPLETE'
