import json
import logging
import retrying

import sdk_cmd
import sdk_tasks

from tests import config

log = logging.getLogger(__name__)


def restart_broker_pods(service_name=config.SERVICE_NAME):
    for i in range(config.DEFAULT_BROKER_COUNT):
        pod_name = "{}-{}".format(config.DEFAULT_POD_TYPE, i)
        task_name = "{}-{}".format(pod_name, "broker")
        broker_id = sdk_tasks.get_task_ids(service_name, task_name)
        rc, stdout, _ = sdk_cmd.svc_cli(
            config.PACKAGE_NAME, service_name, "pod restart {}".format(pod_name)
        )
        assert rc == 0, "Pod restart {} failed".format(pod_name)
        restart_info = json.loads(stdout)
        assert len(restart_info) == 2
        assert restart_info["tasks"][0] == task_name
        sdk_tasks.check_tasks_updated(service_name, task_name, broker_id)
        sdk_tasks.check_running(service_name, config.DEFAULT_BROKER_COUNT)


def replace_broker_pod(service_name=config.SERVICE_NAME):
    pod_name = "{}-0".format(config.DEFAULT_POD_TYPE)
    task_name = "{}-{}".format(pod_name, "broker")
    broker_0_id = sdk_tasks.get_task_ids(service_name, task_name)
    sdk_cmd.svc_cli(config.PACKAGE_NAME, service_name, "pod replace {}".format(pod_name))
    sdk_tasks.check_tasks_updated(service_name, task_name, broker_0_id)
    sdk_tasks.check_running(service_name, config.DEFAULT_BROKER_COUNT)
    # wait till all brokers register


def wait_for_topic(package_name: str, service_name: str, topic_name: str):
    """
    Execute `dcos kafka topic describe` to wait for topic creation.
    """

    @retrying.retry(
        stop_max_delay=5 * 60 * 1000,
        wait_exponential_multiplier=1000,
        wait_exponential_max=60 * 1000,
    )
    def describe(topic):
        rc, _, _ = sdk_cmd.svc_cli(package_name, service_name, "topic describe {}".format(topic))
        assert rc == 0

    describe(topic_name)
