import pytest

import sdk_install as install
import sdk_tasks as tasks
import sdk_marathon as marathon
import shakedown
import dcos
import dcos.config
import dcos.http

from tests.test_utils import (
    DEFAULT_PARTITION_COUNT,
    DEFAULT_REPLICATION_FACTOR,
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_BROKER_COUNT,
    DEFAULT_TOPIC_NAME,
    EPHEMERAL_TOPIC_NAME,
    DEFAULT_POD_TYPE,
    DEFAULT_PLAN_NAME,
    DEFAULT_PHASE_NAME,
    DEFAULT_TASK_NAME,
    DEPLOY_STRATEGY_SERIAL_CANARY,
    service_cli,
    broker_count_check,
    service_plan_wait
)


def setup_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)
    shakedown.install_package(PACKAGE_NAME,
                              service_name=SERVICE_NAME,
                              options_json=install.get_package_options(
                                  additional_options=DEPLOY_STRATEGY_SERIAL_CANARY
                              ),
                              wait_for_completion=True)


def teardown_module(module):
    install.uninstall(SERVICE_NAME, PACKAGE_NAME)


# --------- Deploy Strategy -------------


@pytest.mark.smoke
@pytest.mark.sanity
def test_canary_init():

    pl = service_plan_wait(DEFAULT_PLAN_NAME)
    brokers = service_cli('broker list')
    assert brokers == []

    assert pl['status'] == 'WAITING'
    assert pl['phases'][0]['status'] == 'WAITING'

    assert pl['phases'][0]['steps'][0]['status'] == 'WAITING'
    assert pl['phases'][0]['steps'][1]['status'] == 'WAITING'
    if DEFAULT_BROKER_COUNT > 2:
        assert pl['phases'][0]['steps'][2]['status'] == 'PENDING'


@pytest.mark.smoke
@pytest.mark.sanity
def test_canary_first():
 
    service_cli('plan continue {} {}'.format(DEFAULT_PLAN_NAME, DEFAULT_PHASE_NAME))

    tasks.check_running(SERVICE_NAME, 1)

    broker_count_check(1)

    # do not use service_plan always
    # when here, plan should always return properly
    pl = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))

    assert pl['status'] == 'WAITING'
    assert pl['phases'][0]['status'] == 'WAITING'

    assert pl['phases'][0]['steps'][0]['status'] == 'COMPLETE'
    assert pl['phases'][0]['steps'][1]['status'] == 'WAITING'

    if DEFAULT_BROKER_COUNT >2:
        assert pl['phases'][0]['steps'][2]['status'] == 'PENDING'



@pytest.mark.smoke
@pytest.mark.sanity
def test_canary_second():

    service_cli('plan continue {} {}'.format(DEFAULT_PLAN_NAME, DEFAULT_PHASE_NAME))

    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    broker_count_check(DEFAULT_BROKER_COUNT)

    pl = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))
    assert pl['status'] == 'COMPLETE'
    assert pl['phases'][0]['status'] == 'COMPLETE'

    for step in range(DEFAULT_BROKER_COUNT):
        assert pl['phases'][0]['steps'][step]['status'] == 'COMPLETE'


@pytest.mark.smoke
@pytest.mark.sanity
def test_no_change():

    broker_ids = tasks.get_task_ids(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE))
    plan1 = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))

    config = marathon.get_config(SERVICE_NAME)
    marathon.update_app(SERVICE_NAME, config)

    plan2 = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))

    assert plan1 == plan2
    try:
        tasks.check_tasks_updated(SERVICE_NAME, '{}-'.format(DEFAULT_POD_TYPE), broker_ids, timeout_seconds=60)
        assert False, "Should not restart tasks now"
    except AssertionError as arg:
        raise arg
    except:
        pass

    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    assert plan2['status'] == 'COMPLETE'
    assert plan2['phases'][0]['status'] == 'COMPLETE'

    for step in range(DEFAULT_BROKER_COUNT):
        assert plan2['phases'][0]['steps'][step]['status'] == 'COMPLETE'


@pytest.mark.smoke
@pytest.mark.sanity
def test_increase_count():

    config = marathon.get_config(SERVICE_NAME)
    config['env']['BROKER_COUNT'] = str(int(config['env']['BROKER_COUNT']) + 1)
    marathon.update_app(SERVICE_NAME, config)

    try:
        tasks.check_running(PACKAGE_NAME, DEFAULT_BROKER_COUNT + 1, timeout_seconds=60)
        assert False, "Should not start task now"
    except AssertionError as arg:
        raise arg
    except:
        pass  # expected to fail

    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT)

    pl = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))
    assert pl['status'] == 'WAITING'
    assert pl['phases'][0]['status'] == 'WAITING'

    for step in range(DEFAULT_BROKER_COUNT):
        assert pl['phases'][0]['steps'][step]['status'] == 'COMPLETE'

    assert pl['phases'][0]['steps'][DEFAULT_BROKER_COUNT]['status'] == 'WAITING'

    service_cli('plan continue {} {}'.format(DEFAULT_PLAN_NAME, DEFAULT_PHASE_NAME))

    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT + 1)

    broker_count_check(DEFAULT_BROKER_COUNT + 1)

    pl = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))
    assert pl['status'] == 'COMPLETE'
    assert pl['phases'][0]['status'] == 'COMPLETE'

    for step in range(DEFAULT_BROKER_COUNT + 1):
        assert pl['phases'][0]['steps'][step]['status'] == 'COMPLETE'


@pytest.mark.smoke
@pytest.mark.sanity
def test_increase_cpu():
    def plan_waiting():
        try:
            pl = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))
            if pl['status'] == 'WAITING':
                return True
        except:
            pass
        return False

    def plan_complete():
        try:
            pl = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))
            if pl['status'] == 'COMPLETE':
                return True
        except:
            pass
        return False

    config = marathon.get_config(SERVICE_NAME)
    config['env']['BROKER_CPUS'] = str(0.1 + float(config['env']['BROKER_CPUS']))
    marathon.update_app(SERVICE_NAME, config)

    shakedown.wait_for(plan_waiting)

    pl = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))
    assert pl['status'] == 'WAITING'
    assert pl['phases'][0]['status'] == 'WAITING'

    assert pl['phases'][0]['steps'][0]['status'] == 'WAITING'
    assert pl['phases'][0]['steps'][1]['status'] == 'WAITING'
    for step in range (2, DEFAULT_BROKER_COUNT +1 ):
        assert pl['phases'][0]['steps'][step]['status'] == 'PENDING'

    # all tasks are still running
    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT + 1)

    broker_ids = tasks.get_task_ids(SERVICE_NAME, '{}-0-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME))

    service_cli('plan continue {} {}'.format(DEFAULT_PLAN_NAME, DEFAULT_PHASE_NAME))

    tasks.check_tasks_updated(SERVICE_NAME, '{}-0-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME), broker_ids)

    tasks.check_running(SERVICE_NAME, DEFAULT_BROKER_COUNT + 1)

    pl = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))

    assert pl['status'] == 'WAITING'
    assert pl['phases'][0]['status'] == 'WAITING'

    assert pl['phases'][0]['steps'][0]['status'] == 'COMPLETE'
    assert pl['phases'][0]['steps'][1]['status'] == 'WAITING'

    for step in range(2, DEFAULT_BROKER_COUNT + 1):
        assert pl['phases'][0]['steps'][step]['status'] == 'PENDING'

    broker_ids = tasks.get_task_ids(SERVICE_NAME, '{}-1-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME))

    service_cli('plan continue {} {}'.format(DEFAULT_PLAN_NAME, DEFAULT_PHASE_NAME))

    tasks.check_tasks_updated(SERVICE_NAME, '{}-1-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME), broker_ids)

    shakedown.wait_for(plan_complete)

    pl = service_cli('plan show {}'.format(DEFAULT_PLAN_NAME))

    assert pl['status'] == 'COMPLETE'
    assert pl['phases'][0]['status'] == 'COMPLETE'
    for step in range(DEFAULT_BROKER_COUNT + 1):
        assert pl['phases'][0]['steps'][step]['status'] == 'COMPLETE'

    broker_count_check(DEFAULT_BROKER_COUNT + 1)

