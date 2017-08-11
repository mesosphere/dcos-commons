import json
import logging
import pytest
import time

import shakedown

import sdk_cmd as cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils

RECOVERY_TIMEOUT_SECONDS = 20 * 60

from tests.config import (
    PACKAGE_NAME,
    DEFAULT_TASK_COUNT
)

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(PACKAGE_NAME)
        sdk_install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@sdk_utils.dcos_1_9_or_higher  # dcos task exec not supported < 1.9
def test_node_replace_replaces_seed_node():
    pod_to_replace = 'node-0'

    # start replace and wait for it to finish
    cmd.run_cli('cassandra pod replace {}'.format(pod_to_replace))
    sdk_plan.wait_for_kicked_off_recovery(PACKAGE_NAME)
    sdk_plan.wait_for_completed_recovery(PACKAGE_NAME, timeout_seconds=RECOVERY_TIMEOUT_SECONDS)


@pytest.mark.sanity
@pytest.mark.local
@sdk_utils.dcos_1_9_or_higher  # dcos task exec not supported < 1.9
def test_node_replace_replaces_node():
    pod_to_replace = 'node-2'
    pod_host = get_pod_host(pod_to_replace)
    log.info('avoid host for pod {}: {}'.format(pod_to_replace, pod_host))

    # Update the placement constraints so the new node doesn't end up on the same host
    config = sdk_marathon.get_config(PACKAGE_NAME)
    config['env']['PLACEMENT_CONSTRAINT'] = 'hostname:UNLIKE:{}'.format(pod_host)
    sdk_marathon.update_app(PACKAGE_NAME, config)

    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)

    # start replace and wait for it to finish
    cmd.run_cli('cassandra pod replace {}'.format(pod_to_replace))
    sdk_plan.wait_for_kicked_off_recovery(PACKAGE_NAME)
    sdk_plan.wait_for_completed_recovery(PACKAGE_NAME, timeout_seconds=RECOVERY_TIMEOUT_SECONDS)


@pytest.mark.sanity
@pytest.mark.recovery
@pytest.mark.shutdown_node
def test_shutdown_host_test():
    scheduler_ip = shakedown.get_service_ips('marathon', PACKAGE_NAME).pop()
    log.info('marathon ip = {}'.format(scheduler_ip))

    node_ip = None
    pod_name = None
    for pod_id in range(0, DEFAULT_TASK_COUNT):
        pod_name = 'node-{}'.format(pod_id)
        pod_host = get_pod_host(pod_name)
        if pod_host != scheduler_ip:
            node_ip = pod_host
            break

    assert node_ip is not None, 'Could not find a node to shut down'

    old_agent = get_pod_agent(pod_name)
    log.info('pod name = {}, node_ip = {}, agent = {}'.format(pod_name, node_ip, old_agent))

    task_ids = sdk_tasks.get_task_ids(PACKAGE_NAME, pod_name)

    # instead of partitioning or reconnecting, we shut down the host permanently
    status, stdout = shakedown.run_command_on_agent(node_ip, 'sudo shutdown -h +1')
    log.info('shutdown agent {}: [{}] {}'.format(node_ip, status, stdout))

    assert status is True

    log.info('sleeping 100s after shutting down agent')
    time.sleep(100)

    cmd.run_cli('cassandra pod replace {}'.format(pod_name))
    sdk_tasks.check_tasks_updated(PACKAGE_NAME, pod_name, task_ids)

    # double check that all tasks are running
    sdk_tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)
    sdk_plan.wait_for_completed_deployment(PACKAGE_NAME)

    new_agent = get_pod_agent(pod_name)
    assert old_agent != new_agent


def get_pod_agent(pod_name):
    stdout = cmd.run_cli('cassandra pod info {}'.format(pod_name), print_output=False)
    return json.loads(stdout)[0]['info']['slaveId']['value']


def get_pod_host(pod_name):
    stdout = cmd.run_cli('cassandra pod info {}'.format(pod_name), print_output=False)
    labels = json.loads(stdout)[0]['info']['labels']['labels']
    for i in range(0, len(labels)):
        if labels[i]['key'] == 'offer_hostname':
            return labels[i]['value']
    return None
