import dcos.http
import inspect
import os
import pytest
import shakedown

from tests.test_utils import (
    DEFAULT_TASK_COUNT,
    PACKAGE_NAME,
    check_health,
    get_marathon_config,
    get_deployment_plan,
    marathon_api_url,
    request,
    uninstall,
    spin
)


strict_mode = os.getenv('SECURITY', 'permissive')


def setup_module(module):
    uninstall()

    if strict_mode == 'strict':
        shakedown.install_package_and_wait(
            package_name=PACKAGE_NAME,
            options_file=os.path.dirname(os.path.abspath(inspect.getfile(inspect.currentframe()))) + "/strict.json")
    else:
        shakedown.install_package_and_wait(
            package_name=PACKAGE_NAME,
            options_file=None)

    check_health()


def teardown_module(module):
    uninstall()


@pytest.mark.sanity
def test_install_worked():
    pass


@pytest.mark.sanity
def test_bump_metadata_cpus():
    check_health()
    meta_data_ids = get_task_ids('meta-data')
    print('meta-data ids: ' + str(meta_data_ids))

    data_ids = get_task_ids('data')
    print('data ids: ' + str(data_ids))

    config = get_marathon_config()
    cpus = float(config['env']['METADATA_CPU'])
    config['env']['METADATA_CPU'] = str(cpus + 0.1)
    r = request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    tasks_updated('meta-data', meta_data_ids)
    tasks_not_updated('data', data_ids)

    check_health()


@pytest.mark.sanity
def test_bump_data_nodes():
    check_health()

    meta_data_ids = get_task_ids('meta-data')
    print('meta-data ids: ' + str(meta_data_ids))

    data_ids = get_task_ids('data')
    print('data ids: ' + str(data_ids))

    config = get_marathon_config()
    dataNodeCount = int(config['env']['DATA_COUNT']) + 1
    config['env']['DATA_COUNT'] = str(dataNodeCount)
    r = request(
        dcos.http.put,
        marathon_api_url('apps/' + PACKAGE_NAME),
        json=config)

    check_health(DEFAULT_TASK_COUNT + 1)
    tasks_not_updated('meta-data', meta_data_ids)
    tasks_not_updated('data', data_ids)


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

