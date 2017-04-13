import pytest
from tests.config import *
import sdk_install as install
import sdk_tasks as tasks
import sdk_utils
import json
import shakedown
import time
import sdk_cmd as cmd


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    sdk_utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False)


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@pytest.mark.recovery
@pytest.mark.shutdown_node
def test_shutdown_host_test():

    service_ip = shakedown.get_service_ips(PACKAGE_NAME).pop()
    sdk_utils.out('marathon ip = {}'.format(service_ip))

    node_ip = 0
    for pod_id in range(0, DEFAULT_TASK_COUNT):
        node_ip = get_pod_host(pod_id)
        if node_ip != service_ip:
            break

    if node_ip is None:
        assert Fail, 'could not find a node to shutdown'

    old_agent = get_pod_agent(pod_id)
    sdk_utils.out('pod id = {},  node_ip = {}, agent = {}'.format(pod_id, node_ip, old_agent))

    task_ids = tasks.get_task_ids(PACKAGE_NAME, 'node-{}'.format(pod_id))

    # instead of partition/reconnect, we shutdown host permanently
    status, stdout = shakedown.run_command_on_agent(node_ip, 'sudo shutdown -h +1')
    sdk_utils.out('shutdown agent {}: [{}] {}'.format(node_ip, status, stdout))
    assert status is True
    time.sleep(100)

    cmd.run_cli('cassandra pods replace node-{}'.format(pod_id))

    tasks.check_tasks_updated(PACKAGE_NAME, 'node', task_ids)

    #double check all tasks are running
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)
    new_agent = get_pod_agent(pod_id)

    assert old_agent != new_agent


def get_pod_agent(id):
    stdout = cmd.run_cli('cassandra pods info node-{}'.format(id))
    return json.loads(stdout)[0]['info']['slaveId']['value']


def get_pod_label(id):
    stdout = cmd.run_cli('cassandra pods info node-{}'.format(id))
    return json.loads(stdout)[0]['info']['labels']['labels']


def get_pod_host(id):
    labels = get_pod_label(id)
    for i in range(0, len(labels)):
        if labels[i]['key'] == 'offer_hostname':
            return labels[i]['value']
    return None
