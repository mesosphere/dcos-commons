import dcos.http
import json
import pytest
import re
import shakedown

from tests.test_utils import (
    DEFAULT_TASK_COUNT,
    PACKAGE_NAME,
    check_health,
    get_marathon_config,
    get_deployment_plan,
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


def teardown_module(module):
    uninstall()


@pytest.mark.sanity
def test_install_worked():
    pass


@pytest.mark.sanity
def test_bump_hello_cpus():
    assert check_health()
    hello_ids = get_task_ids('hello')
    print('hello ids: ' + str(hello_ids))

    config = get_marathon_config()
    cpus = float(config['env']['HELLO_CPUS'])
    config['env']['HELLO_CPUS'] = str(cpus + 0.1)
    request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    assert tasks_updated('hello', hello_ids)
    assert check_health()


@pytest.mark.sanity
def test_bump_hello_nodes():
    assert check_health()

    hello_ids = get_task_ids('hello')
    print('hello ids: ' + str(hello_ids))

    config = get_marathon_config()
    nodeCount = int(config['env']['HELLO_COUNT']) + 1
    config['env']['HELLO_COUNT'] = str(nodeCount)
    request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    assert check_health(DEFAULT_TASK_COUNT + 1)
    assert tasks_not_updated('hello', hello_ids)
    DEFAULT_TASK_COUNT += 1 # for use in later tests


@pytest.mark.sanity
def test_pods_list():
    stdout = run_dcos_cli_cmd('hello-world pods list')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == DEFAULT_TASK_COUNT
    for i in range(len(jsonobj)):
        assert jsonobj[i] == 'hello-{}'.format(i)


@pytest.mark.sanity
def test_pods_status_all():
    stdout = run_dcos_cli_cmd('hello-world pods status')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == DEFAULT_TASK_COUNT
    for k,v in jsonobj.items():
        assert re.match('hello-[0-9]+', k)
        assert len(v) == 1
        task = v[0]
        assert len(task) == 3
        assert re.match('hello-[0-9]+-server__[0-9a-f-]+', task['id'])
        assert re.match('hello-[0-9]+-server', task['name'])
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
    stdout = run_dcos_cli_cmd('hello-world pods info hello-0')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 1
    task = jsonobj[0]
    assert len(task) == 2
    assert task['info']['name'] == 'hello-0-server'
    assert task['info']['taskId']['value'] == task['status']['taskId']['value']
    assert task['status']['state'] == 'TASK_RUNNING'


@pytest.mark.sanity
def test_pods_restart():
    hello_ids = get_task_ids('hello')

    # get current agent id:
    stdout = run_dcos_cli_cmd('hello-world pods info hello-0')
    old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    stdout = run_dcos_cli_cmd('hello-world pods restart hello-0')
    jsonobj = json.loads(stdout)
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'hello-0-server'

    assert tasks_updated('hello', hello_ids)
    assert check_health(DEFAULT_TASK_COUNT)

    # check agent didn't move:
    stdout = run_dcos_cli_cmd('hello-world pods info hello-0')
    new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    assert old_agent == new_agent


@pytest.mark.sanity
def test_pods_replace():
    hello_ids = get_task_ids('hello')

    # get current agent id:
    stdout = run_dcos_cli_cmd('hello-world pods info hello-0')
    old_agent = json.loads(stdout)[0]['info']['slaveId']['value']

    jsonobj = json.loads(run_dcos_cli_cmd('hello-world pods replace hello-0'))
    assert len(jsonobj) == 2
    assert jsonobj['pod'] == 'hello-0'
    assert len(jsonobj['tasks']) == 1
    assert jsonobj['tasks'][0] == 'hello-0-server'

    assert tasks_updated('hello', hello_ids)
    assert check_health(DEFAULT_TASK_COUNT)

    # check agent moved:
    stdout = run_dcos_cli_cmd('hello-world pods info hello-0')
    new_agent = json.loads(stdout)[0]['info']['slaveId']['value']
    # TODO: enable assert if/when agent is guaranteed to change (may randomly move back to old agent)
    #assert old_agent != new_agent


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

