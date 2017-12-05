import logging
import retrying

import sdk_cmd
import sdk_tasks
import shakedown
from tests import config

log = logging.getLogger(__name__)


def broker_count_check(count, service_name=config.SERVICE_NAME):
    def fun():
        try:
            if len(sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'broker list', json=True)) == count:
                return True
        except:
            pass
        return False

    shakedown.wait_for(fun)


def restart_broker_pods(service_name=config.SERVICE_NAME):
    for i in range(config.DEFAULT_BROKER_COUNT):
        pod_name = '{}-{}'.format(config.DEFAULT_POD_TYPE, i)
        task_name = '{}-{}'.format(pod_name, config.DEFAULT_TASK_NAME)
        broker_id = sdk_tasks.get_task_ids(service_name, task_name)
        restart_info = sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'pod restart {}'.format(pod_name), json=True)
        assert len(restart_info) == 2
        assert restart_info['tasks'][0] == task_name
        sdk_tasks.check_tasks_updated(service_name, task_name, broker_id)
        sdk_tasks.check_running(service_name, config.DEFAULT_BROKER_COUNT)


def replace_broker_pod(service_name=config.SERVICE_NAME):
    pod_name = '{}-0'.format(config.DEFAULT_POD_TYPE)
    task_name = '{}-{}'.format(pod_name, config.DEFAULT_TASK_NAME)
    broker_0_id = sdk_tasks.get_task_ids(service_name, task_name)
    sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'pod replace {}'.format(pod_name))
    sdk_tasks.check_tasks_updated(service_name, task_name, broker_0_id)
    sdk_tasks.check_running(service_name, config.DEFAULT_BROKER_COUNT)
    # wait till all brokers register
    broker_count_check(config.DEFAULT_BROKER_COUNT, service_name=service_name)


def create_topic(topic_name, service_name=config.SERVICE_NAME):
    # Get the list of topics that exist before we create a new topic
    topic_list_before = sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'topic list', json=True)

    create_info = sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'topic create {}'.format(topic_name), json=True)
    log.info(create_info)
    assert ('Created topic "%s".\n' % topic_name in create_info['message'])

    if '.' in topic_name or '_' in topic_name:
        assert ("topics with a period ('.') or underscore ('_') could collide." in create_info['message'])

    topic_list_after = sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'topic list', json=True)

    new_topics = set(topic_list_after) - set(topic_list_before)
    assert topic_name in new_topics

    topic_info = sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'topic describe {}'.format(topic_name), json=True)
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == config.DEFAULT_PARTITION_COUNT


def delete_topic(topic_name, service_name=config.SERVICE_NAME):
    delete_info = sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'topic delete {}'.format(topic_name), json=True)
    assert len(delete_info) == 1
    assert delete_info['message'].startswith('Output: Topic {} is marked for deletion'.format(topic_name))

    topic_info = sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, 'topic describe {}'.format(topic_name), json=True)
    assert len(topic_info) == 1
    assert len(topic_info['partitions']) == config.DEFAULT_PARTITION_COUNT


def wait_for_topic(kafka: dict, topic_name: str):
    """
    Execute `dcos kafka topic describe` to wait for topic creation.
    """
    @retrying.retry(wait_exponential_multiplier=1000,
                    wait_exponential_max=60 * 1000)
    def describe(topic):
        sdk_cmd.svc_cli(kafka["package_name"], kafka["service"]["name"],
                        "topic describe {}".format(topic),
                        json=True)

    describe(topic_name)


def assert_topic_lists_are_equal_without_automatic_topics(expected, actual):
    """Check for equality in topic lists after filtering topics that start with
    an underscore."""
    filtered_actual = list(filter(lambda x: not x.startswith('_'), actual))
    assert expected == filtered_actual
