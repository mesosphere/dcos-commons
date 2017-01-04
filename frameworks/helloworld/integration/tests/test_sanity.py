import dcos.http
import dcos.marathon
import json
import pytest
import re
import shakedown

from tests.test_utils import (
    PACKAGE_NAME,
    check_health,
    get_marathon_config,
    get_deployment_plan,
    get_task_count,
    install,
    marathon_api_url,
    request,
    run_dcos_cli_cmd,
    uninstall,
    spin
)


def setup_module(module):
    uninstall()
    install()
    check_health()


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
    check_health()
    hello_ids = get_task_ids('hello')
    print('hello ids: ' + str(hello_ids))

    config = get_marathon_config()
    cpus = float(config['env']['HELLO_CPUS'])
    config['env']['HELLO_CPUS'] = str(cpus + 0.1)
    request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    tasks_updated('hello', hello_ids)
    check_health()


@pytest.mark.sanity
def test_bump_hello_nodes():
    check_health()

    hello_ids = get_task_ids('hello')
    print('hello ids: ' + str(hello_ids))

    config = get_marathon_config()
    nodeCount = int(config['env']['HELLO_COUNT']) + 1
    config['env']['HELLO_COUNT'] = str(nodeCount)
    request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    check_health()
    tasks_not_updated('hello', hello_ids)


@pytest.mark.sanity
def test_pods_list():
    stdout = run_dcos_cli_cmd('hello-world pods list')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == get_task_count()
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
    stdout = run_dcos_cli_cmd('hello-world pods status')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == get_task_count()
    for k,v in jsonobj.items():
        assert re.match('(hello|world)-[0-9]+', k)
        assert len(v) == 1
        task = v[0]
        assert len(task) == 3
        assert re.match('(hello|world)-[0-9]+-server__[0-9a-f-]+', task['id'])
        assert re.match('(hello|world)-[0-9]+-server', task['name'])
        assert task['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_pods_status_one():
    stdout = run_dcos_cli_cmd('hello-world pods status hello-0')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 1
    task = jsonobj[0]
    assert len(task) == 3
    assert re.match('hello-0-server__[0-9a-f-]+', task['id'])
    assert task['name'] == 'hello-0-server'
    assert task['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_pods_info():
    stdout = run_dcos_cli_cmd('hello-world pods info world-1')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 1
    task = jsonobj[0]
    assert len(task) == 2
    assert task['info']['name'] == 'world-1-server'
    assert task['info']['taskId']['value'] == task['status']['taskId']['value']
    assert task['status']['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_pods_restart():
    hello_ids = get_task_ids('hello-0')

    # get current agent id:
    stdout = run_dcos_cli_cmd('hello-world pods info hello-0')
    old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    stdout = run_dcos_cli_cmd('hello-world pods restart hello-0')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'hello-0-server'

    tasks_updated('hello', hello_ids)
    check_health()

    # check agent didn't move:
    stdout = run_dcos_cli_cmd('hello-world pods info hello-0')
    new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    assert old_agent == new_agent


@pytest.mark.sanity
def test_pods_replace():
    world_ids = get_task_ids('world-0')

    # get current agent id:
    stdout = run_dcos_cli_cmd('hello-world pods info world-0')
    old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    jsonobj = json.loads(run_dcos_cli_cmd('hello-world pods replace world-0'))
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'world-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'world-0-server'

    tasks_updated('world-0', world_ids)
    check_health()

    # check agent moved:
    stdout = run_dcos_cli_cmd('hello-world pods info world-0')
    new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    # TODO: enable assert if/when agent is guaranteed to change (may randomly move back to old agent)
    #assert old_agent != new_agent


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
    fn = lambda: marathon_client.get_app(app_id).get(
        "lastTaskFailure", {}).get("timestamp", None)
    success = lambda timestamp: (timestamp != old_timestamp,
                                 "second scheduler has not yet failed")
    spin(fn, success)

    # Verify ZK is unchanged
    zk_config_new = shakedown.get_zk_node_data(zk_path)
    assert zk_config_old == zk_config_new


def get_task_ids(prefix):
    tasks = shakedown.get_service_tasks(PACKAGE_NAME)
    prefixed_tasks = [t for t in tasks if t['name'].startswith(prefix)]
    task_ids = [t['id'] for t in prefixed_tasks]
    return task_ids


def tasks_updated(prefix, old_task_ids):
    def fn():
        try:
            return get_task_ids(prefix)
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(task_ids):
        print('Old task ids: ' + str(old_task_ids))
        print('New task ids: ' + str(task_ids))
        success = True

        for id in task_ids:
            print('Checking ' + id)
            if id in old_task_ids:
                success = False

        if not len(task_ids) >= len(old_task_ids):
            success = False

        print('Waiting for update to ' + prefix)
        return (
            success,
            'Task type:' + prefix + ' not updated'
        )

    return spin(fn, success_predicate)


def tasks_not_updated(prefix, old_task_ids):
    def fn():
        try:
            return get_task_ids(prefix)
        except dcos.errors.DCOSHTTPException:
            return []

    def success_predicate(task_ids):
        print('Old task ids: ' + str(old_task_ids))
        print('New task ids: ' + str(task_ids))
        success = True

        for id in old_task_ids:
            print('Checking ' + id)
            if id not in task_ids:
                success = False

        if not len(task_ids) >= len(old_task_ids):
            success = False

        print('Determining no update occurred for ' + prefix)
        return (
            success,
            'Task type:' + prefix + ' not updated'
        )

    return spin(fn, success_predicate)
