import json
import logging

import pytest

import shakedown
import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils
from tests.config import (
    PACKAGE_NAME
)

log = logging.getLogger(__name__)


# global pytest variable applicable to whole module
pytestmark = sdk_utils.dcos_1_9_or_higher

@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME)
        # due to canary: no tasks should launch, and suppressed shouldn't be set
        sdk_install.install(
            PACKAGE_NAME,
            0,
            additional_options={
                'service': {'spec_file': 'examples/canary.yml'},
                'hello': {'count': 4},
                'world': {'count': 4}
            },
            wait_for_deployment=False)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
def test_canary_init():
    def fn():
        return sdk_cmd.run_cli('hello-world pod list')
    assert json.loads(shakedown.wait_for(fn, noisy=True)) == []

    pl = sdk_plan.wait_for_plan_status(PACKAGE_NAME, 'deploy', 'WAITING')
    log.info(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'WAITING'
    assert steps[1]['status'] == 'WAITING'
    assert steps[2]['status'] == 'PENDING'
    assert steps[3]['status'] == 'PENDING'

    phase = pl['phases'][1]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'WAITING'
    assert steps[1]['status'] == 'WAITING'
    assert steps[2]['status'] == 'PENDING'
    assert steps[3]['status'] == 'PENDING'


@pytest.mark.sanity
def test_canary_first():
    sdk_cmd.run_cli('hello-world plan continue deploy hello-deploy')

    expected_tasks = ['hello-0']
    sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks))
    assert json.loads(sdk_cmd.run_cli('hello-world pod list')) == expected_tasks

    # do not use service_plan always
    # when here, plan should always return properly
    pl = sdk_plan.wait_for_completed_step(PACKAGE_NAME, 'deploy', 'hello-deploy', 'hello-0:[server]')
    log.info(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'WAITING'
    assert steps[2]['status'] == 'PENDING'
    assert steps[3]['status'] == 'PENDING'

    phase = pl['phases'][1]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'WAITING'
    assert steps[1]['status'] == 'WAITING'
    assert steps[2]['status'] == 'PENDING'
    assert steps[3]['status'] == 'PENDING'


@pytest.mark.sanity
def test_canary_plan_continue_noop():
    sdk_cmd.run_cli('hello-world plan continue deploy')

    # the plan doesn't have the waiting bit set, so telling it to continue should be a no-op
    # (the plan is currently just in WAITING for display purposes)
    expected_tasks = ['hello-0']
    try:
        sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks) + 1, timeout_seconds=30)
        assert False, "Shouldn't have deployed a second task"
    except AssertionError as arg:
        raise arg
    except:
        pass # expected
    sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks))

    assert json.loads(sdk_cmd.run_cli('hello-world pod list')) == expected_tasks


@pytest.mark.sanity
def test_canary_second():
    sdk_cmd.run_cli('hello-world plan continue deploy world-deploy')
    sdk_plan.wait_for_step_status(PACKAGE_NAME, 'deploy', 'world-deploy', 'world-0:[server]', 'PENDING')

    # because the plan strategy is serial, the second phase just clears a wait bit without
    # proceeding to launch anything:
    expected_tasks = ['hello-0']
    try:
        sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks) + 1, timeout_seconds=30)
        assert False, "Shouldn't have deployed a second task"
    except AssertionError as arg:
        raise arg
    except:
        pass # expected
    sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks))

    assert json.loads(sdk_cmd.run_cli('hello-world pod list')) == expected_tasks

    pl = sdk_plan.get_deployment_plan(PACKAGE_NAME)
    log.info(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'WAITING'
    assert steps[2]['status'] == 'PENDING'
    assert steps[3]['status'] == 'PENDING'

    phase = pl['phases'][1]
    assert phase['status'] == 'PENDING'
    steps2 = phase['steps']
    assert len(steps) == 4
    assert steps2[0]['status'] == 'PENDING'
    assert steps2[1]['status'] == 'WAITING'
    assert steps2[2]['status'] == 'PENDING'
    assert steps2[3]['status'] == 'PENDING'


@pytest.mark.sanity
def test_canary_third():
    sdk_cmd.run_cli('hello-world plan continue deploy hello-deploy')

    expected_tasks = [
        'hello-0', 'hello-1', 'hello-2', 'hello-3',
        'world-0']
    sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks))
    assert json.loads(sdk_cmd.run_cli('hello-world pod list')) == expected_tasks

    pl = sdk_plan.wait_for_completed_phase(PACKAGE_NAME, 'deploy', 'hello-deploy')
    log.info(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'

    phase = pl['phases'][1]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'WAITING'
    assert steps[2]['status'] == 'PENDING'
    assert steps[3]['status'] == 'PENDING'


@pytest.mark.sanity
def test_canary_fourth():
    sdk_cmd.run_cli('hello-world plan continue deploy world-deploy')

    expected_tasks = [
        'hello-0', 'hello-1', 'hello-2', 'hello-3',
        'world-0', 'world-1', 'world-2', 'world-3']
    sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks))
    assert json.loads(sdk_cmd.run_cli('hello-world pod list')) == expected_tasks

    pl = sdk_plan.wait_for_completed_plan(PACKAGE_NAME, 'deploy')
    log.info(pl)

    assert pl['status'] == 'COMPLETE'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'

    phase = pl['phases'][1]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'


@pytest.mark.sanity
def test_increase_count():
    sdk_marathon.bump_task_count_config(PACKAGE_NAME, 'HELLO_COUNT')

    expected_tasks = [
        'hello-0', 'hello-1', 'hello-2', 'hello-3',
        'world-0', 'world-1', 'world-2', 'world-3']
    try:
        sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks) + 1, timeout_seconds=60)
        assert False, "Should not start task now"
    except AssertionError as arg:
        raise arg
    except:
        pass # expected to fail
    sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks))
    assert json.loads(sdk_cmd.run_cli('hello-world pod list')) == expected_tasks

    pl = sdk_plan.wait_for_plan_status(PACKAGE_NAME, 'deploy', 'WAITING')
    log.info(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 5
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'
    assert steps[4]['status'] == 'WAITING'

    phase = pl['phases'][1]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'

    sdk_cmd.run_cli('hello-world plan continue deploy hello-deploy')

    expected_tasks = [
        'hello-0', 'hello-1', 'hello-2', 'hello-3', 'hello-4',
        'world-0', 'world-1', 'world-2', 'world-3']
    sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks))
    assert json.loads(sdk_cmd.run_cli('hello-world pod list')) == expected_tasks

    pl = sdk_plan.wait_for_plan_status(PACKAGE_NAME, 'deploy', 'COMPLETE')
    log.info(pl)

    assert pl['status'] == 'COMPLETE'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 5
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'
    assert steps[4]['status'] == 'COMPLETE'

    phase = pl['phases'][1]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'


@pytest.mark.sanity
def test_increase_cpu():
    hello_0_ids = sdk_tasks.get_task_ids(PACKAGE_NAME, 'hello-0-server')
    sdk_marathon.bump_cpu_count_config(PACKAGE_NAME, 'HELLO_CPUS')

    pl = sdk_plan.wait_for_plan_status(PACKAGE_NAME, 'deploy', 'WAITING')
    log.info(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 5
    assert steps[0]['status'] == 'WAITING'
    assert steps[1]['status'] == 'WAITING'
    assert steps[2]['status'] == 'PENDING'
    assert steps[3]['status'] == 'PENDING'
    assert steps[4]['status'] == 'PENDING'

    phase = pl['phases'][1]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'

    # check that all prior tasks are still running, no changes yet
    expected_tasks = [
        'hello-0', 'hello-1', 'hello-2', 'hello-3', 'hello-4',
        'world-0', 'world-1', 'world-2', 'world-3']
    sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks))
    assert json.loads(sdk_cmd.run_cli('hello-world pod list')) == expected_tasks
    assert hello_0_ids == sdk_tasks.get_task_ids(PACKAGE_NAME, 'hello-0-server')

    sdk_cmd.run_cli('hello-world plan continue deploy hello-deploy')

    sdk_tasks.check_tasks_updated(PACKAGE_NAME, 'hello-0-server', hello_0_ids)
    sdk_tasks.check_running(PACKAGE_NAME, len(expected_tasks))

    pl = sdk_plan.wait_for_step_status(PACKAGE_NAME, 'deploy', 'hello-deploy', 'hello-0:[server]', 'COMPLETE')
    log.info(pl)

    assert pl['status'] == 'WAITING'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'WAITING'
    steps = phase['steps']
    assert len(steps) == 5
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'WAITING'
    assert steps[2]['status'] == 'PENDING'
    assert steps[3]['status'] == 'PENDING'
    assert steps[4]['status'] == 'PENDING'

    phase = pl['phases'][1]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'

    hello_1_ids = sdk_tasks.get_task_ids(PACKAGE_NAME, 'hello-1-server')
    sdk_cmd.run_cli('hello-world plan continue deploy hello-deploy')
    sdk_tasks.check_tasks_updated(PACKAGE_NAME, 'hello-1-server', hello_1_ids)

    pl = sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)
    log.info(pl)

    assert pl['status'] == 'COMPLETE'

    assert len(pl['phases']) == 2

    phase = pl['phases'][0]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 5
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'
    assert steps[4]['status'] == 'COMPLETE'

    phase = pl['phases'][1]
    assert phase['status'] == 'COMPLETE'
    steps = phase['steps']
    assert len(steps) == 4
    assert steps[0]['status'] == 'COMPLETE'
    assert steps[1]['status'] == 'COMPLETE'
    assert steps[2]['status'] == 'COMPLETE'
    assert steps[3]['status'] == 'COMPLETE'
