import logging
import os

import pytest

import sdk_cmd
import sdk_plan
import sdk_tasks
import sdk_upgrade
import sdk_utils
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


@pytest.mark.soak_upgrade
def test_soak_upgrade_downgrade():
    sdk_upgrade.soak_upgrade_downgrade(
        config.PACKAGE_NAME,
        config.SERVICE_NAME,
        config.DEFAULT_TASK_COUNT)


@pytest.mark.soak_pod_pause
def test_pause_single_task():
    # get current agent id:
    taskinfo = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-0', json=True)[0]['info']
    old_agent = taskinfo['slaveId']['value']
    old_cmd = taskinfo['command']['value']

    # sanity check of pod status/plan status before we pause/resume:
    jsonobj = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod status hello-0 --json', json=True)
    assert len(jsonobj['tasks']) == 2
    assert jsonobj['tasks'][0]['name'] == 'hello-0-server'
    assert jsonobj['tasks'][0]['status'] == 'RUNNING'
    phase = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'plan status deploy --json', json=True)['phases'][0]
    assert phase['name'] == 'hello'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][0]['name'] == 'hello-0:[server]'
    assert phase['steps'][0]['status'] == 'COMPLETE'

    assert jsonobj['tasks'][1]['name'] == 'hello-0-companion'
    assert jsonobj['tasks'][1]['status'] == 'RUNNING'
    assert phase['name'] == 'hello'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][1]['name'] == 'hello-0:[companion]'
    assert phase['steps'][1]['status'] == 'COMPLETE'

    # pause the task, wait for it to relaunch
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-0')
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'debug pod pause hello-0 server', json=True
    )
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'hello-0-server'
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello-0', hello_ids)
    config.check_running()

    # check agent didn't move, and that the command has changed:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-0', json=True
    )
    assert len(jsonobj) == 1
    assert old_agent == jsonobj[0]['info']['slaveId']['value']
    cmd = jsonobj[0]['info']['command']['value']
    assert 'This task is PAUSED' in cmd

    readiness_check = jsonobj[0]['info']['check']['command']['command']['value']
    assert 'exit 1' == readiness_check

    # check PAUSED state in plan and in pod status:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(jsonobj['tasks']) == 2
    assert jsonobj['tasks'][0]['name'] == 'hello-0-server'
    assert jsonobj['tasks'][0]['status'] == 'PAUSED'
    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'plan status deploy --json', json=True
    )['phases'][0]
    assert phase['name'] == 'hello'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][0]['name'] == 'hello-0:[server]'
    assert phase['steps'][0]['status'] == 'PAUSED'

    # check companion is still running
    assert jsonobj['tasks'][1]['name'] == 'hello-0-companion'
    assert jsonobj['tasks'][0]['status'] == 'RUNNING'
    assert phase['name'] == 'hello'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][1]['name'] == 'hello-0:[companion]'
    assert phase['steps'][1]['status'] == 'COMPLETE'

    # resume the pod again, wait for it to relaunch
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-0')
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'debug pod resume hello-0', json=Tru
    )
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'hello-0-server'
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello-0', hello_ids)
    config.check_running()

    # check again that the agent didn't move:
    taskinfo = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-0', json=True
    )[0]['info']
    assert old_agent == taskinfo['slaveId']['value']
    assert old_cmd == taskinfo['command']['value']

    # check that the pod/plan status is back to normal:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(jsonobj['tasks']) == 2
    assert jsonobj['tasks'][0]['name'] == 'hello-0-server'
    assert jsonobj['tasks'][0]['status'] == 'RUNNING'
    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'plan status deploy --json', json=True
    )['phases'][0]
    assert phase['name'] == 'hello'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][0]['name'] == 'hello-0:[server]'
    assert phase['steps'][0]['status'] == 'COMPLETE'


@pytest.mark.soak_pod_pause
def test_pause_all_pod_tasks():
    # get current agent id:
    podinfo = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-0', json=True
    )
    old_agent = podinfo[0]['info']['slaveId']['value']
    old_cmds = (podinfo[0]['info']['command']['value'], podinfo[1]['info']['command']['value'])

    # sanity check of pod status/plan status before we pause/resume:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(jsonobj['tasks']) == 2
    assert jsonobj['tasks'][0]['name'] == 'hello-0-server'
    assert jsonobj['tasks'][0]['status'] == 'RUNNING'
    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'plan status deploy --json', json=True
    )['phases'][0]
    assert phase['name'] == 'hello'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][0]['name'] == 'hello-0:[server]'
    assert phase['steps'][0]['status'] == 'COMPLETE'

    assert jsonobj['tasks'][1]['name'] == 'hello-0-companion'
    assert jsonobj['tasks'][1]['status'] == 'RUNNING'
    assert phase['name'] == 'hello'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][1]['name'] == 'hello-0:[companion]'
    assert phase['steps'][1]['status'] == 'COMPLETE'

    # pause the pod, wait for it to relaunch
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-0')
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'debug pod pause hello-0', json=True
    )
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 2
    assert jsonobj['tasks'][0] == 'hello-0-server'
    assert jsonobj['tasks'][1] == 'hello-0-companion'
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello-0', hello_ids)
    config.check_running()

    # check agent didn't move, and that the command has changed:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-0', json=True
    )
    assert len(jsonobj) == 2
    assert old_agent == jsonobj[0]['info']['slaveId']['value']
    cmd = jsonobj[0]['info']['command']['value']
    assert 'This task is PAUSED' in cmd
    cmd = jsonobj[1]['info']['command']['value']
    assert 'This task is PAUSED' in cmd

    readiness_check = jsonobj[0]['info']['check']['command']['command']['value']
    assert 'exit 1' == readiness_check
    readiness_check = jsonobj[1]['info']['check']['command']['command']['value']
    assert 'exit 1' == readiness_check

    # check PAUSED state in plan and in pod status:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(jsonobj['tasks']) == 2
    assert jsonobj['tasks'][0]['name'] == 'hello-0-server'
    assert jsonobj['tasks'][0]['status'] == 'PAUSED'
    assert jsonobj['tasks'][1]['name'] == 'hello-0-companion'
    assert jsonobj['tasks'][1]['status'] == 'PAUSED'

    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'plan status deploy --json', json=True
    )['phases'][0]
    assert phase['name'] == 'hello'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][0]['name'] == 'hello-0:[server]'
    assert phase['steps'][0]['status'] == 'PAUSED'
    assert phase['steps'][1]['name'] == 'hello-0:[companion]'
    assert phase['steps'][1]['status'] == 'PAUSED'

    # resume the pod again, wait for it to relaunch
    hello_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-0')
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'debug pod resume hello-0', json=Tru
    )
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 2
    assert jsonobj['tasks'][0] == 'hello-0-server'
    assert jsonobj['tasks'][1] == 'hello-0-companion'
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello-0', hello_ids)
    config.check_running()

    # check again that the agent didn't move:
    podinfo = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-0', json=True
    )[0]['info']
    assert old_agent == podinfo[0]['info']['slaveId']['value']
    assert (
        old_cmds ==
        (podinfo[0]['info']['command']['value'], podinfo[1]['info']['command']['value'])
    )

    # check that the pod/plan status is back to normal:
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod status hello-0 --json', json=True
    )
    assert len(jsonobj['tasks']) == 2
    assert jsonobj['tasks'][0]['name'] == 'hello-0-server'
    assert jsonobj['tasks'][0]['status'] == 'RUNNING'
    assert jsonobj['tasks'][1]['name'] == 'hello-0-companion'
    assert jsonobj['tasks'][1]['status'] == 'RUNNING'
    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'plan status deploy --json', json=True
    )['phases'][0]
    assert phase['name'] == 'hello'
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
            config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-{}'.format(i), json=True
        )[0]['info']
        pod_agents.append(task_info['slaveId']['value'])
        pod_commands.append(task_info['command']['value'])

    # check that their respective deploy steps are complete, and their tasks are running
    for i in range(10):
        pod_status = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            'pod status hello-{} --json'.format(i),
            json=True
        )
        assert len(pod_status['tasks']) == 1
        assert pod_status['tasks'][0]['name'] == 'hello-0-server'
        assert pod_status['tasks'][0]['status'] == 'RUNNING'

        phase = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, config.SERVICE_NAME, 'plan status deploy --json', json=True
        )['phases'][i]
        assert phase['name'] == 'hello'
        assert phase['status'] == 'COMPLETE'
        assert phase['steps'][0]['name'] == 'hello-{}:[server]'.format(i)
        assert phase['steps'][0]['status'] == 'COMPLETE'

    # get current task ids for all pods
    pod_task_ids = []
    for i in range(10):
        pod_task_ids.append(sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-{}'.format(i)))

    # pause all hello pods
    pause_results = []
    for i in range(10):
        pause_results.append(sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            'debug pod pause hello-{}'.format(i),
            json=True
        ))

    # verify pauses were all successful
    for i, pause_result in enumerate(pause_results):
        assert len(pause_result) == 2
        assert pause_result['pod'] == 'hello-{}'.format(i)
        assert len(pause_result['tasks']) == 1
        assert pause_result['tasks'][0] == 'hello-{}-server'.format(i)
        sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello-{}'.format(i), pod_task_ids[i])
    config.check_running()

    # verify that they're on the agents, and with different commands
    for i in range(10):
        pod_info = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-{}'.format(i), json=True
        )
        assert len(pod_info) == 1
        assert pod_agents[i] == pod_info[0]['info']['slaveId']['value']
        cmd = pod_info[0]['info']['command']['value']
        assert 'This task is PAUSED' in cmd

        readiness_check = pod_info[0]['info']['check']['command']['command']['value']
        assert 'exit 1' == readiness_check

    # verify they've all reached the PAUSED state in plan and pod status:
    for i in range(10):
        pod_status = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            'pod status hello-{} --json'.format(i),
            json=True
        )
        assert len(pod_status['tasks']) == 1
        assert pod_status['tasks'][0]['name'] == 'hello-{}-server'.format(i)
        assert pod_status['tasks'][0]['status'] == 'PAUSED'

        phase = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            'plan status deploy --json',
            json=True
        )['phases'][i]
        assert phase['name'] == 'hello'
        assert phase['status'] == 'COMPLETE'
        assert phase['steps'][0]['name'] == 'hello-{}:[server]'.format(i)
        assert phase['steps'][0]['status'] == 'PAUSED'

    # verify that the 11th hello pod is unaffacted
    jsonobj = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'pod status hello-10 --json', json=True
    )
    assert len(jsonobj['tasks']) == 2
    assert jsonobj['tasks'][0]['name'] == 'hello-10-server'
    assert jsonobj['tasks'][0]['status'] == 'RUNNING'
    phase = sdk_cmd.svc_cli(
        config.PACKAGE_NAME, config.SERVICE_NAME, 'plan status deploy --json', json=True
    )['phases'][10]
    assert phase['name'] == 'hello'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][0]['name'] == 'hello-10:[server]'
    assert phase['steps'][0]['status'] == 'COMPLETE'

    assert jsonobj['tasks'][1]['name'] == 'hello-10-companion'
    assert jsonobj['tasks'][1]['status'] == 'RUNNING'
    assert phase['name'] == 'hello'
    assert phase['status'] == 'COMPLETE'
    assert phase['steps'][1]['name'] == 'hello-10:[companion]'
    assert phase['steps'][1]['status'] == 'COMPLETE'


    # get paused task ids
    paused_pod_task_ids = []
    for i in range(10):
        paused_pod_task_ids.append(
            sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-{}'.format(i))
        )

    # resume all pods
    resume_results = []
    for i in range(10):
        resume_results.append(sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            'debug pod resume hello-{}'.format(i),
            json=True
        ))

    # verify that the resumes were successful
    for i, resume_result in enumerate(resume_results):
        assert len(resume_result) == 2
        assert resume_result['pod'] == 'hello-{}'.format(i)
        assert len(resume_result['tasks']) == 1
        assert resume_result['tasks'][0] == 'hello-{}-server'.format(i)
        sdk_tasks.check_tasks_updated(
            config.SERVICE_NAME, 'hello-{}'.format(i), paused_pod_task_ids[i]
        )
    config.check_running()

    # verify that the agents are still the same, and the commands are restored
    for i in range(10):
        task_info = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info hello-{}'.format(i), json=True
        )[0]['info']
        assert pod_agents[i] == task_info['slaveId']['value']
        assert pod_commands[i] == task_info['command']['value']

    # verify they've all reached the COMPLETE state in plan and pod status:
    for i in range(10):
        pod_status = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            'pod status hello-{} --json'.format(i),
            json=True
        )
        assert len(pod_status['tasks']) == 1
        assert pod_status['tasks'][0]['name'] == 'hello-{}-server'.format(i)
        assert pod_status['tasks'][0]['status'] == 'RUNNING'

        phase = sdk_cmd.svc_cli(
            config.PACKAGE_NAME,
            config.SERVICE_NAME,
            'plan status deploy --json',
            json=True
        )['phases'][i]
        assert phase['name'] == 'hello'
        assert phase['status'] == 'COMPLETE'
        assert phase['steps'][0]['name'] == 'hello-{}:[server]'.format(i)
        assert phase['steps'][0]['status'] == 'COMPLETE'


@pytest.mark.soak_secrets_update
@pytest.mark.dcos_min_version('1.10')
def test_soak_secrets_update():

    secret_content_alternative = "hello-world-secret-data-alternative"
    test_soak_secrets_framework_alive()

    sdk_cmd.run_cli("package install --cli dcos-enterprise-cli --yes")
    sdk_cmd.run_cli("package install --cli hello-world --yes")
    sdk_cmd.run_cli("security secrets update --value={} secrets/secret1".format(secret_content_alternative))
    sdk_cmd.run_cli("security secrets update --value={} secrets/secret2".format(secret_content_alternative))
    sdk_cmd.run_cli("security secrets update --value={} secrets/secret3".format(secret_content_alternative))
    test_soak_secrets_restart_hello0()

    # get new task ids - only first pod
    hello_tasks = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "hello-0")
    world_tasks = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "world-0")

    # make sure content is changed
    assert secret_content_alternative == sdk_tasks.task_exec(world_tasks[0], "bash -c 'echo $WORLD_SECRET1_ENV'")[1]
    assert secret_content_alternative == sdk_tasks.task_exec(world_tasks[0], "cat WORLD_SECRET2_FILE")[1]
    assert secret_content_alternative == sdk_tasks.task_exec(world_tasks[0], "cat secrets/secret3")[1]

    # make sure content is changed
    assert secret_content_alternative == sdk_tasks.task_exec(hello_tasks[0], "bash -c 'echo $HELLO_SECRET1_ENV'")[1]
    assert secret_content_alternative == sdk_tasks.task_exec(hello_tasks[0], "cat HELLO_SECRET1_FILE")[1]
    assert secret_content_alternative == sdk_tasks.task_exec(hello_tasks[0], "cat HELLO_SECRET2_FILE")[1]

    # revert back to some other value
    sdk_cmd.run_cli("security secrets update --value=SECRET1 secrets/secret1")
    sdk_cmd.run_cli("security secrets update --value=SECRET2 secrets/secret2")
    sdk_cmd.run_cli("security secrets update --value=SECRET3 secrets/secret3")
    test_soak_secrets_restart_hello0()


@pytest.mark.soak_secrets_alive
@pytest.mark.dcos_min_version('1.10')
def test_soak_secrets_framework_alive():

    sdk_plan.wait_for_completed_deployment(FRAMEWORK_NAME)
    sdk_tasks.check_running(FRAMEWORK_NAME, NUM_HELLO + NUM_WORLD)


def test_soak_secrets_restart_hello0():

    hello_tasks_old = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "hello-0")
    world_tasks_old = sdk_tasks.get_task_ids(FRAMEWORK_NAME, "world-0")

    # restart pods to retrieve new secret's content
    sdk_cmd.svc_cli(config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod restart hello-0')
    sdk_cmd.svc_cli(config.PACKAGE_NAME, FRAMEWORK_NAME, 'pod restart world-0')

    # wait pod restart to complete
    sdk_tasks.check_tasks_updated(FRAMEWORK_NAME, "hello-0", hello_tasks_old)
    sdk_tasks.check_tasks_updated(FRAMEWORK_NAME, 'world-0', world_tasks_old)

    # wait till it all running
    sdk_tasks.check_running(FRAMEWORK_NAME, NUM_HELLO + NUM_WORLD)
