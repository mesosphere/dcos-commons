import dcos.marathon
import json
import pytest
import re
import shakedown

import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_spin as spin
import sdk_tasks as tasks

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT,
    configured_task_count,
    check_running
)


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)


@pytest.mark.sanity
def test_no_colocation_in_podtypes():
    # check that no two 'hellos' and no two 'worlds' are colocated on the same agent
    all_tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    print(all_tasks)
    hello_agents = []
    world_agents = []
    for task in all_tasks:
        if task['name'].startswith('hello-'):
            hello_agents.append(task['slave_id'])
        elif task['name'].startswith('world-'):
            world_agents.append(task['slave_id'])
        else:
            assert False, "Unknown task: " + task['name']
    assert len(hello_agents) == len(set(hello_agents))
    assert len(world_agents) == len(set(world_agents))


@pytest.mark.sanity
def test_bump_hello_cpus():
    check_running()
    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')
    print('hello ids: ' + str(hello_ids))

    config = marathon.get_config(PACKAGE_NAME)
    cpus = float(config['env']['HELLO_CPUS'])
    config['env']['HELLO_CPUS'] = str(cpus + 0.1)
    cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    tasks.check_tasks_updated(PACKAGE_NAME, 'hello', hello_ids)
    check_running()


@pytest.mark.sanity
def test_bump_world_cpus():
    check_running()
    world_ids = tasks.get_task_ids(PACKAGE_NAME, 'world')
    print('world ids: ' + str(world_ids))

    config = marathon.get_config(PACKAGE_NAME)
    cpus = float(config['env']['WORLD_CPUS'])
    config['env']['WORLD_CPUS'] = str(cpus + 0.1)
    cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    tasks.check_tasks_updated(PACKAGE_NAME, 'world', world_ids)
    check_running()


@pytest.mark.sanity
def test_bump_hello_nodes():
    check_running()

    hello_ids = tasks.get_task_ids(PACKAGE_NAME, 'hello')
    print('hello ids: ' + str(hello_ids))

    config = marathon.get_config(PACKAGE_NAME)
    node_count = int(config['env']['HELLO_COUNT']) + 1
    config['env']['HELLO_COUNT'] = str(node_count)
    cmd.request('put', marathon.api_url('apps/' + PACKAGE_NAME), json=config)

    check_running()
    tasks.check_tasks_not_updated(PACKAGE_NAME, 'hello', hello_ids)


@pytest.mark.sanity
def test_pods_list():
    stdout = cmd.run_cli('hello-world pods list')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == configured_task_count()
    # expect: X instances of 'hello-#' followed by Y instances of 'world-#',
    # in alphanumerical order
    first_world = -1
    for i in range(len(jsonobj)):
        entry = jsonobj[i]
        if first_world < 0:
            if entry.startswith('world-'):
                first_world = i
        if first_world == -1:
            assert jsonobj[i] == 'hello-{}'.format(i)
        else:
            assert jsonobj[i] == 'world-{}'.format(i - first_world)


@pytest.mark.sanity
def test_pods_status_all():
    stdout = cmd.run_cli('hello-world pods status')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == configured_task_count()
    for k, v in jsonobj.items():
        assert re.match('(hello|world)-[0-9]+', k)
        assert len(v) == 1
        task = v[0]
        assert len(task) == 3
        assert re.match('(hello|world)-[0-9]+-server__[0-9a-f-]+', task['id'])
        assert re.match('(hello|world)-[0-9]+-server', task['name'])
        assert task['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_pods_status_one():
    stdout = cmd.run_cli('hello-world pods status hello-0')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 1
    task = jsonobj[0]
    assert len(task) == 3
    assert re.match('hello-0-server__[0-9a-f-]+', task['id'])
    assert task['name'] == 'hello-0-server'
    assert task['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_pods_info():
    stdout = cmd.run_cli('hello-world pods info world-1')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 1
    task = jsonobj[0]
    assert len(task) == 2
    assert task['info']['name'] == 'world-1-server'
    assert task['info']['taskId']['value'] == task['status']['taskId']['value']
    assert task['status']['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_lock():
    '''This test verifies that a second scheduler fails to startup when
    an existing scheduler is running.  Without locking, the scheduler
    would fail during registration, but after writing its config to ZK.
    So in order to verify that the scheduler fails immediately, we ensure
    that the ZK config state is unmodified.'''

    marathon_client = dcos.marathon.create_client()

    # Get ZK state from running framework
    zk_path = "dcos-service-{}/ConfigTarget".format(PACKAGE_NAME)
    zk_config_old = shakedown.get_zk_node_data(zk_path)

    # Get marathon app
    app_id = "/{}".format(PACKAGE_NAME)
    app = marathon_client.get_app(app_id)
    old_timestamp = app.get("lastTaskFailure", {}).get("timestamp", None)

    # Scale to 2 instances
    labels = app["labels"]
    labels.pop("MARATHON_SINGLE_INSTANCE_APP")
    marathon_client.update_app(app_id, {"labels": labels})
    shakedown.deployment_wait()
    marathon_client.update_app(app_id, {"instances": 2})

    # Wait for second scheduler to fail
    def fn():
        timestamp = marathon_client.get_app(app_id).get("lastTaskFailure", {}).get("timestamp", None)
        return timestamp != old_timestamp
    spin.time_wait_noisy(lambda: fn())

    # Verify ZK is unchanged
    zk_config_new = shakedown.get_zk_node_data(zk_path)
    assert zk_config_old == zk_config_new
