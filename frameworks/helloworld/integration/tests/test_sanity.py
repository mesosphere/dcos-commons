import dcos.http
import pytest
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
    r = request(
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
    r = request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    check_health(DEFAULT_TASK_COUNT + 1)
    tasks_not_updated('hello', hello_ids)


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

