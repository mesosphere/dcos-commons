import json
import logging

import shakedown
import sdk_cmd
import sdk_tasks

from tests.config import (
    PACKAGE_NAME,
    SERVICE_NAME,
    DEFAULT_PARTITION_COUNT,
    DEFAULT_BROKER_COUNT,
    DEFAULT_POD_TYPE,
    DEFAULT_TASK_NAME
)

log = logging.getLogger(__name__)

DEFAULT_TOPIC_NAME = 'topic1'
EPHEMERAL_TOPIC_NAME = 'topic_2'


def broker_count_check(count, service_name=SERVICE_NAME):
    def fun():
        try:
            if len(sdk_cmd.svc_cli(PACKAGE_NAME, service_name, 'broker list')) == count:
                return True
        except:
            pass
        return False

    shakedown.wait_for(fun)


def restart_broker_pods(service_name=SERVICE_NAME):
    for i in range(DEFAULT_BROKER_COUNT):
        broker_id = sdk_tasks.get_task_ids(service_name,'{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME))
        restart_info = sdk_cmd.svc_cli(PACKAGE_NAME, service_name, 'pod restart {}-{}'.format(DEFAULT_POD_TYPE, i), json=True)
        sdk_tasks.check_tasks_updated(service_name, '{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME), broker_id)
        sdk_tasks.check_running(service_name, DEFAULT_BROKER_COUNT)
        assert len(restart_info) == 2
        assert restart_info['tasks'][0] == '{}-{}-{}'.format(DEFAULT_POD_TYPE, i, DEFAULT_TASK_NAME)


def replace_broker_pod(service_name=SERVICE_NAME):
    broker_0_id = sdk_tasks.get_task_ids(service_name, '{}-0-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME))
    sdk_cmd.svc_cli(PACKAGE_NAME, service_name, 'pod replace {}-0'.format(DEFAULT_POD_TYPE))
    sdk_tasks.check_tasks_updated(service_name, '{}-0-{}'.format(DEFAULT_POD_TYPE, DEFAULT_TASK_NAME), broker_0_id)
    sdk_tasks.check_running(service_name, DEFAULT_BROKER_COUNT)
    # wait till all brokers register
    broker_count_check(DEFAULT_BROKER_COUNT, service_name=service_name)


def create_topic(service_name=SERVICE_NAME):
    create_info = sdk_cmd.svc_cli(PACKAGE_NAME, service_name, 'topic create {}'.format(EPHEMERAL_TOPIC_NAME), json=True)
    log.info(create_info)
    assert ('Created topic "%s".\n' % EPHEMERAL_TOPIC_NAME in create_info['message'])
    assert ("topics with a period ('.') or underscore ('_') could collide." in create_info['message'])
    topic_list_info = sdk_cmd.svc_cli(PACKAGE_NAME, service_name, 'topic list', json=True)
    assert topic_list_info == [EPHEMERAL_TOPIC_NAME]

    topic_info = sdk_cmd.svc_cli(PACKAGE_NAME, service_name, 'topic describe {}'.format(EPHEMERAL_TOPIC_NAME), json=True)
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT


def delete_topic(service_name=SERVICE_NAME):
    delete_info = sdk_cmd.svc_cli(PACKAGE_NAME, service_name, 'topic delete {}'.format(EPHEMERAL_TOPIC_NAME), json=True)

    assert len(delete_info) == 1
    assert delete_info['message'].startswith('Output: Topic {} is marked for deletion'.format(EPHEMERAL_TOPIC_NAME))

    topic_info = sdk_cmd.svc_cli(PACKAGE_NAME, service_name, 'topic describe {}'.format(EPHEMERAL_TOPIC_NAME), json=True)
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == DEFAULT_PARTITION_COUNT
