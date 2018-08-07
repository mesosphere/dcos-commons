import logging
import os

import pytest
import retrying

import sdk_cmd
import sdk_tasks
from tests import config

log = logging.getLogger(__name__)

FRAMEWORK_NAME = "secrets/hello-world"
NUM_HELLO = 2
NUM_WORLD = 3

# check environment first...
if "FRAMEWORK_NAME" in os.environ:
    FRAMEWORK_NAME = os.environ["FRAMEWORK_NAME"]
if "NUM_HELLO" in os.environ:
    NUM_HELLO = int(os.environ["NUM_HELLO"])
if "NUM_WORLD" in os.environ:
    NUM_WORLD = int(os.environ["NUM_WORLD"])


def get_task_status(pod_status, task_name):
    return [t for t in pod_status['tasks'] if t['name'] == task_name][0]


def get_task_info(pod_info, task_name):
    return [t for t in pod_info if t['info']['name'] == task_name][0]['info']


@retrying.retry(wait_fixed=1000, stop_max_delay=30 * 1000)
def wait_for_state(state, pod_name, task_names):
    pod_status = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod status {} --json'.format(pod_name), json=True
    )

    task_statuses = [
        get_task_status(pod_status, '{}-{}'.format(pod_name, task_name))
        for task_name in task_names
    ]
    assert all([task_status['status'] == state for task_status in task_statuses])


@pytest.mark.soak_pod_pause
def test_pause_single_task():
    # get current agent id:
    task_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod info hello-0', json=True
    )[0]['info']
    old_agent = task_info['slaveId']['value']
    old_cmd = task_info['command']['value']

    # sanity check of pod status/plan status before we pause/resume:
    pod_status = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(pod_status['tasks']) == 2
    wait_for_state('RUNNING', 'hello-0', ['server'])

    phases = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'plan status deploy --json', json=True
    )['phases']
    phase = phases[0]
    assert phase['name'] == 'hello-deploy'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][0]['name'] == 'hello-0:[server]'
    assert phase['steps'][0]['status'] == 'COMPLETE'
    assert phase['steps'][1]['name'] == 'hello-0:[companion]'
    assert phase['steps'][1]['status'] == 'COMPLETE'

    wait_for_state('RUNNING', 'hello-0', ['companion'])

    # pause the task, wait for it to relaunch
    hello_ids = sdk_tasks.get_task_ids(FRAMEWORK_NAME, 'hello-0')
    pause_result = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'debug pod pause hello-0 -t server', json=True
    )
    assert len(pause_result) == 2
    assert pause_result['pod'] == 'hello-0'
    assert len(pause_result['tasks']) == 1
    assert pause_result['tasks'][0] == 'hello-0-server'
    sdk_tasks.check_tasks_updated(FRAMEWORK_NAME, 'hello-0', hello_ids)
    config.check_running(service_name=FRAMEWORK_NAME)

    # check agent didn't move, and that the command has changed:
    pod_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod info hello-0', json=True
    )
    assert len(pod_info) == 2
    task_info = get_task_info(pod_info, 'hello-0-server')
    assert old_agent == task_info['slaveId']['value']
    cmd = task_info['command']['value']
    assert 'This task is PAUSED' in cmd

    readiness_check = task_info['check']['command']['command']['value']
    assert 'exit 1' == readiness_check

    # check PAUSED state
    pod_status = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(pod_status['tasks']) == 2
    wait_for_state('PAUSED', 'hello-0', ['server'])

    # check companion is still running
    wait_for_state('RUNNING', 'hello-0', ['companion'])

    # resume the pod again, wait for it to relaunch
    hello_ids = sdk_tasks.get_task_ids(FRAMEWORK_NAME, 'hello-0')
    resume_result = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'debug pod resume hello-0 -t server', json=True
    )
    assert len(resume_result) == 2
    assert resume_result['pod'] == 'hello-0'
    assert len(resume_result['tasks']) == 1
    assert resume_result['tasks'][0] == 'hello-0-server'
    sdk_tasks.check_tasks_updated(FRAMEWORK_NAME, 'hello-0', hello_ids)
    config.check_running(service_name=FRAMEWORK_NAME)

    # check again that the agent didn't move:
    task_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod info hello-0', json=True
    )[0]['info']
    assert old_agent == task_info['slaveId']['value']
    assert old_cmd == task_info['command']['value']

    # check that the pod/plan status is back to normal:
    pod_status = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(pod_status['tasks']) == 2
    wait_for_state('RUNNING', 'hello-0', ['server'])

    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'plan status deploy --json', json=True
    )['phases'][0]
    assert phase['name'] == 'hello-deploy'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][0]['name'] == 'hello-0:[server]'
    assert phase['steps'][0]['status'] == 'COMPLETE'


@pytest.mark.soak_pod_pause
def test_pause_all_pod_tasks():
    # get current agent id:
    pod_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod info hello-0', json=True
    )
    task_info = get_task_info(pod_info, 'hello-0-server')
    old_agent = task_info['slaveId']['value']
    old_server_cmd = task_info['command']['value']
    task_info = get_task_info(pod_info, 'hello-0-companion')
    old_companion_cmd = task_info['command']['value']

    # sanity check of pod status/plan status before we pause/resume:
    pod_status = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(pod_status['tasks']) == 2
    wait_for_state('RUNNING', 'hello-0', ['server'])

    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'plan status deploy --json', json=True
    )['phases'][0]
    assert phase['name'] == 'hello-deploy'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][0]['name'] == 'hello-0:[server]'
    assert phase['steps'][0]['status'] == 'COMPLETE'
    assert phase['steps'][1]['name'] == 'hello-0:[companion]'
    assert phase['steps'][1]['status'] == 'COMPLETE'

    wait_for_state('RUNNING', 'hello-0', ['companion'])

    # pause the pod, wait for it to relaunch
    hello_ids = sdk_tasks.get_task_ids(FRAMEWORK_NAME, 'hello-0')
    pause_result = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'debug pod pause hello-0', json=True
    )
    assert len(pause_result) == 2
    assert pause_result['pod'] == 'hello-0'
    assert len(pause_result['tasks']) == 2
    assert 'hello-0-server' in pause_result['tasks']
    assert 'hello-0-companion' in pause_result['tasks']
    sdk_tasks.check_tasks_updated(FRAMEWORK_NAME, 'hello-0', hello_ids)
    config.check_running(service_name=FRAMEWORK_NAME)

    # check agent didn't move, and that the commands have changed:
    pod_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod info hello-0', json=True
    )
    assert len(pod_info) == 2
    task_info = get_task_info(pod_info, 'hello-0-server')
    assert old_agent == task_info['slaveId']['value']
    cmd = task_info['command']['value']
    assert 'This task is PAUSED' in cmd
    readiness_check = task_info['check']['command']['command']['value']
    assert 'exit 1' == readiness_check

    task_info = get_task_info(pod_info, 'hello-0-companion')
    assert old_agent == task_info['slaveId']['value']
    cmd = task_info['command']['value']
    assert 'This task is PAUSED' in cmd
    readiness_check = task_info['check']['command']['command']['value']
    assert 'exit 1' == readiness_check

    # check PAUSED state
    pod_status = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(pod_status['tasks']) == 2
    wait_for_state('PAUSED', 'hello-0', ['server', 'companion'])

    # resume the pod again, wait for it to relaunch
    hello_ids = sdk_tasks.get_task_ids(FRAMEWORK_NAME, 'hello-0')
    resume_result = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'debug pod resume hello-0', json=True
    )
    assert len(resume_result) == 2
    assert resume_result['pod'] == 'hello-0'
    assert len(resume_result['tasks']) == 2
    assert 'hello-0-server' in resume_result['tasks']
    assert 'hello-0-companion' in resume_result['tasks']
    sdk_tasks.check_tasks_updated(FRAMEWORK_NAME, 'hello-0', hello_ids)
    config.check_running(service_name=FRAMEWORK_NAME)

    # check again that the agent didn't move:
    pod_info = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod info hello-0', json=True
    )
    task_info = get_task_info(pod_info, 'hello-0-server')
    assert old_agent == task_info['slaveId']['value']
    assert old_server_cmd == task_info['command']['value']

    task_info = get_task_info(pod_info, 'hello-0-companion')
    assert old_agent == task_info['slaveId']['value']
    assert old_companion_cmd == task_info['command']['value']

    # check that the pod/plan status is back to normal:
    pod_status = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(pod_status['tasks']) == 2
    wait_for_state('RUNNING', 'hello-0', ['server', 'companion'])

    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'plan status deploy --json', json=True
    )['phases'][0]
    assert phase['name'] == 'hello-deploy'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][0]['name'] == 'hello-0:[server]'
    assert phase['steps'][0]['status'] == 'COMPLETE'
    assert phase['steps'][1]['name'] == 'hello-0:[companion]'
    assert phase['steps'][1]['status'] == 'COMPLETE'


@pytest.mark.soak_pod_pause
def test_multiple_pod_pause():
    pod_agents = []
    pod_commands = []

    # get agent id for each hello pod we're pausing:
    for i in range(10):
        task_info = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod info hello-{}'.format(i), json=True
        )[0]['info']
        pod_agents.append(task_info['slaveId']['value'])
        pod_commands.append(task_info['command']['value'])

    # check that their respective deploy steps are complete, and their tasks are running
    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'plan status deploy --json', json=True
    )['phases'][0]
    assert phase['name'] == 'hello-deploy'
    assert phase['status'] == 'COMPLETE'

    for i in range(10):
        pod_status = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            FRAMEWORK_NAME,
            'pod status hello-{} --json'.format(i),
            json=True
        )
        assert len(pod_status['tasks']) == 2
        wait_for_state('RUNNING', 'hello-{}'.format(i), ['server', 'companion'])

        assert phase['steps'][i * 2]['name'] == 'hello-{}:[server]'.format(i)
        assert phase['steps'][i * 2]['status'] == 'COMPLETE'
        assert phase['steps'][i * 2 + 1]['name'] == 'hello-{}:[companion]'.format(i)
        assert phase['steps'][i * 2 + 1]['status'] == 'COMPLETE'

    # get current task ids for all pods
    pod_task_ids = []
    for i in range(10):
        pod_task_ids.append(
            sdk_tasks.get_task_ids(FRAMEWORK_NAME, 'hello-{}-server'.format(i))
        )

    # pause all hello pods
    pause_results = []
    for i in range(10):
        pause_results.append(sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            FRAMEWORK_NAME,
            'debug pod pause hello-{} -t server'.format(i),
            json=True
        ))

    # verify pauses were all successful
    for i, pause_result in enumerate(pause_results):
        assert len(pause_result) == 2
        assert pause_result['pod'] == 'hello-{}'.format(i)
        assert len(pause_result['tasks']) == 1
        assert pause_result['tasks'][0] == 'hello-{}-server'.format(i)
        sdk_tasks.check_tasks_updated(
            FRAMEWORK_NAME, 'hello-{}-server'.format(i), pod_task_ids[i]
        )
    config.check_running(service_name=FRAMEWORK_NAME)

    # verify that they're on the agents, and with different commands
    for i in range(10):
        pod_info = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod info hello-{}'.format(i), json=True
        )
        assert len(pod_info) == 2
        task_info = get_task_info(pod_info, 'hello-{}-server'.format(i))
        assert pod_agents[i] == task_info['slaveId']['value']
        cmd = task_info['command']['value']
        assert 'This task is PAUSED' in cmd

        readiness_check = task_info['check']['command']['command']['value']
        assert 'exit 1' == readiness_check

    # verify they've all reached the PAUSED state in plan and pod status:
    for i in range(10):
        pod_status = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            FRAMEWORK_NAME,
            'pod status hello-{} --json'.format(i),
            json=True
        )
        assert len(pod_status['tasks']) == 2
        wait_for_state('PAUSED', 'hello-{}'.format(i), ['server'])
        wait_for_state('RUNNING', 'hello-{}'.format(i), ['companion'])

    # verify that the 11th hello pod is unaffacted
    pod_status = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod status hello-10 --json', json=True
    )
    assert len(pod_status['tasks']) == 2
    wait_for_state('RUNNING', 'hello-10', ['server', 'companion'])

    assert phase['steps'][20]['name'] == 'hello-10:[server]'
    assert phase['steps'][20]['status'] == 'COMPLETE'
    assert phase['steps'][21]['name'] == 'hello-10:[companion]'
    assert phase['steps'][21]['status'] == 'COMPLETE'

    # get paused task ids
    paused_pod_task_ids = []
    for i in range(10):
        paused_pod_task_ids.append(
            sdk_tasks.get_task_ids(FRAMEWORK_NAME, 'hello-{}-server'.format(i))
        )

    # resume all pods
    resume_results = []
    for i in range(10):
        resume_results.append(sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            FRAMEWORK_NAME,
            'debug pod resume hello-{} -t server'.format(i),
            json=True
        ))

    # verify that the resumes were successful
    for i, resume_result in enumerate(resume_results):
        assert len(resume_result) == 2
        assert resume_result['pod'] == 'hello-{}'.format(i)
        assert len(resume_result['tasks']) == 1
        assert resume_result['tasks'][0] == 'hello-{}-server'.format(i)
        sdk_tasks.check_tasks_updated(
            FRAMEWORK_NAME, 'hello-{}-server'.format(i), paused_pod_task_ids[i]
        )
    config.check_running(service_name=FRAMEWORK_NAME)

    # verify that the agents are still the same, and the commands are restored
    for i in range(10):
        pod_info = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod info hello-{}'.format(i), json=True
        )
        task_info = get_task_info(pod_info, 'hello-{}-server'.format(i))
        assert pod_agents[i] == task_info['slaveId']['value']
        assert pod_commands[i] == task_info['command']['value']

    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, FRAMEWORK_NAME, 'plan status deploy --json', json=True
    )['phases'][0]
    assert phase['name'] == 'hello-deploy'
    assert phase['status'] == 'COMPLETE'

    # verify they've all reached the COMPLETE state in plan and pod status:
    for i in range(10):
        pod_status = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            FRAMEWORK_NAME,
            'pod status hello-{} --json'.format(i),
            json=True
        )
        assert len(pod_status['tasks']) == 2
        wait_for_state('RUNNING', 'hello-{}'.format(i), ['server'])

        assert phase['steps'][i * 2]['name'] == 'hello-{}:[server]'.format(i)
        assert phase['steps'][i * 2]['status'] == 'COMPLETE'
        assert phase['steps'][i * 2 + 1]['name'] == 'hello-{}:[companion]'.format(i)
        assert phase['steps'][i * 2 + 1]['status'] == 'COMPLETE'
