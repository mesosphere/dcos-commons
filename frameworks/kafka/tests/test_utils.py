import pytest
import json

import shakedown
import sdk_cmd
import sdk_tasks as tasks
import sdk_utils as utils

from tests.config import (
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_PARTITION_COUNT,
    DEFAULT_REPLICATION_FACTOR,
    DEFAULT_BROKER_COUNT,
    DEFAULT_PLAN_NAME,
    DEFAULT_PHASE_NAME,
    DEFAULT_POD_TYPE,
    DEFAULT_TASK_NAME
)

DEFAULT_TOPIC_NAME = 'topic1'
EPHEMERAL_TOPIC_NAME = 'topic_2'


def service_cli(cmd_str, get_json=True, print_output=True, service_name=SERVICE_NAME):
    full_cmd = '{} --name={} {}'.format(PACKAGE_NAME, service_name, cmd_str)
    ret_str = sdk_cmd.run_cli(full_cmd, print_output=print_output)
    if get_json:
        return json.loads(ret_str)
    else:
        return ret_str


def broker_count_check(count, service_name=SERVICE_NAME):
    def fun():
        try:
            if len(service_cli('broker list', service_name=service_name)) == count:
                return True
        except:
            pass
        return False

    shakedown.wait_for(fun)


def restart_broker_pods(service_name=SERVICE_NAME):
    for i in range(DEFAULT_BROKER_COUNT):
        broker_id = tasks.get_task_ids(service_name,'{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME))
        restart_info = service_cli('pods restart {}-{}'.format(DEFAULT_POD_TYPE, i), service_name=service_name)
        tasks.check_tasks_updated(service_name, '{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME), broker_id)
        assert len(restart_info) == 2
        assert restart_info['tasks'][0] == '{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME)


def replace_broker_pod(service_name=SERVICE_NAME):
    broker_0_id = tasks.get_task_ids(service_name, '{}-0-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME))
    service_cli('pods replace {}-0'.format(DEFAULT_POD_TYPE), service_name=service_name)
    tasks.check_tasks_updated(service_name, '{}-0-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME), broker_0_id)
    tasks.check_running(service_name, DEFAULT_BROKER_COUNT)
    # wait till all brokers register
    broker_count_check(DEFAULT_BROKER_COUNT, service_name=service_name)


def create_topic(service_name=SERVICE_NAME):
    create_info = service_cli('topic create {}'.format(EPHEMERAL_TOPIC_NAME), service_name=service_name)
    utils.out(create_info)
    assert ('Created topic "%s".\n' % EPHEMERAL_TOPIC_NAME in create_info['message'])
    assert ("topics with a period ('.') or underscore ('_') could collide." in create_info['message'])
    topic_list_info = service_cli('topic list', service_name=service_name)
    assert topic_list_info == [EPHEMERAL_TOPIC_NAME]

    topic_info = service_cli('topic describe {}'.format(EPHEMERAL_TOPIC_NAME), service_name=service_name)
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


def delete_topic(service_name=SERVICE_NAME):
    delete_info = service_cli('topic delete {}'.format(EPHEMERAL_TOPIC_NAME), service_name=service_name)

    assert len(delete_info) == 1
    assert delete_info['message'].startswith('Output: Topic {} is marked for deletion'.format(EPHEMERAL_TOPIC_NAME))

    topic_info = service_cli('topic describe {}'.format(EPHEMERAL_TOPIC_NAME), service_name=service_name)
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT
