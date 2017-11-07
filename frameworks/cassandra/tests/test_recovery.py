import logging
import time

import pytest
import sdk_cmd as cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import shakedown
from tests import config

RECOVERY_TIMEOUT_SECONDS = 20 * 60

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)

        yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)

@pytest.mark.sanity
@pytest.mark.dcos_min_version('1.9', reason='dcos task exec not supported < 1.9')
def test_node_replace_replaces_seed_node():
    pod_to_replace = 'node-0'

    # CASSANDRA-649 Experimental change to potentially resolve flakes: attempt nodetool removenode before replace
    nodetool_decommission(pod_to_replace)

    # start replace and wait for it to finish
    cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace {}'.format(pod_to_replace))
    sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME, timeout_seconds=RECOVERY_TIMEOUT_SECONDS)


@pytest.mark.sanity
@pytest.mark.local
@pytest.mark.dcos_min_version('1.9', reason='dcos task exec not supported < 1.9')
def test_node_replace_replaces_node():
    pod_to_replace = 'node-2'
    pod_host = get_pod_host(pod_to_replace)
    log.info('avoid host for pod {}: {}'.format(pod_to_replace, pod_host))

    # Update the placement constraints so the new node doesn't end up on the same host
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config['env']['PLACEMENT_CONSTRAINT'] = 'hostname:UNLIKE:{}'.format(pod_host)
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)

    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # CASSANDRA-649 Experimental change to potentially resolve flakes: attempt nodetool removenode before replace
    nodetool_decommission(pod_to_replace)

    # start replace and wait for it to finish
    cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace {}'.format(pod_to_replace))
    sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME, timeout_seconds=RECOVERY_TIMEOUT_SECONDS)


@pytest.mark.sanity
@pytest.mark.recovery
@pytest.mark.shutdown_node
def test_shutdown_host_test():
    scheduler_ip = shakedown.get_service_ips('marathon', config.SERVICE_NAME).pop()
    log.info('marathon ip = {}'.format(scheduler_ip))

    node_ip = None
    pod_name = None
    for pod_id in range(0, config.DEFAULT_TASK_COUNT):
        pod_name = 'node-{}'.format(pod_id)
        pod_host = get_pod_host(pod_name)
        if pod_host != scheduler_ip:
            node_ip = pod_host
            break

    assert node_ip is not None, 'Could not find a node to shut down'

    old_agent = get_pod_agent(pod_name)
    log.info('pod name = {}, node_ip = {}, agent = {}'.format(pod_name, node_ip, old_agent))

    task_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, pod_name)

    # instead of partitioning or reconnecting, we shut down the host permanently
    status, stdout = shakedown.run_command_on_agent(node_ip, 'sudo shutdown -h +1')
    log.info('shutdown agent {}: [{}] {}'.format(node_ip, status, stdout))

    assert status is True

    log.info('sleeping 100s after shutting down agent')
    time.sleep(100)

    cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace {}'.format(pod_name))
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, pod_name, task_ids)

    # double check that all tasks are running
    sdk_tasks.check_running(config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)
    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    new_agent = get_pod_agent(pod_name)
    assert old_agent != new_agent


def get_pod_agent(pod_name):
    stdout = cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info {}'.format(pod_name), print_output=False, json=True)
    return stdout[0]['info']['slaveId']['value']


def get_pod_host(pod_name):
    stdout = cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info {}'.format(pod_name), print_output=False, json=True)
    labels = stdout[0]['info']['labels']['labels']
    for i in range(0, len(labels)):
        if labels[i]['key'] == 'offer_hostname':
            return labels[i]['value']
    return None


def nodetool_decommission(target_pod_name):
    '''Performs a 'nodetool decommission' for the specified pod, removing it from the ring.

    There is also 'nodetool removenode <uuid>', but that requires that the target node already be down.
    If the target node is in a UN state, a 'removenode' command fails with an error like this:
    java.lang.UnsupportedOperationException: Node /10.0.2.186 is alive and owns this ID. Use decommission command to remove it from the ring
    '''

    # use a node other than the target to check status:
    all_pods = ['node-{}' for i in range(config.DEFAULT_TASK_COUNT)]
    all_pods.remove(target_pod_name)
    driver_pod_name = all_pods[0]
    log.info('Checking status of {} removal from {}'.format(target_pod_name, driver_pod_name))

    # print "before" status:
    run_nodetool_cmd(driver_pod_name, 'status')

    # perform decommission:
    run_nodetool_cmd(target_pod_name, 'decommission') # THIS MAY TAKE SEVERAL SECONDS

    # print "after" status from different node:
    run_nodetool_cmd(driver_pod_name, 'status')


def run_nodetool_cmd(pod_name, command):
    cmds = [
        'export JAVA_HOME=$(ls -d jre*/)'
        './apache-cassandra-*/bin/nodetool {}'.format(command)]
    cmd.run_cli("task exec {}-server bash -c '{}'".format(pod_name, ' && '.join(cmds)))
