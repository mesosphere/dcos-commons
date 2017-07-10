import json
import pytest
import time

import shakedown

from tests.config import *
import sdk_cmd as cmd
import sdk_install as install
import sdk_jobs as jobs
import sdk_marathon as marathon
import sdk_plan as plan
import sdk_tasks as tasks
import sdk_utils as utils


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False)

    plan.wait_for_completed_deployment(PACKAGE_NAME)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


@pytest.mark.sanity
@utils.dcos_1_9_or_higher # dcos task exec not supported < 1.9
def test_node_replace_replaces_node():
    pod_to_replace = 'node-2'
    pod_host = get_pod_host(pod_to_replace)
    utils.out('avoid host for pod {}: {}'.format(pod_to_replace, pod_host))

    # Update the placement constraints so the new node doesn't end up on the same host
    config = marathon.get_config(PACKAGE_NAME)
    config['env']['PLACEMENT_CONSTRAINT'] = 'hostname:UNLIKE:{}'.format(pod_host)
    marathon.update_app(PACKAGE_NAME, config)

    plan.wait_for_completed_deployment(PACKAGE_NAME)

    # start replace and wait for it to finish
    cmd.run_cli('cassandra pods replace {}'.format(pod_to_replace))
    plan.wait_for_completed_recovery(PACKAGE_NAME)

    # get an exact task id to run 'task exec' against... just in case there's multiple cassandras
    pod_statuses = json.loads(cmd.run_cli('cassandra pods status node-0'))
    task_id = [task['id'] for task in pod_statuses if task['name'] == 'node-0-server'][0]

    # In DC/OS 1.9, task exec does not run in $MESOS_SANDBOX AND does not have access to the envvar.
    if shakedown.dcos_version_less_than('1.10'):
        mesos_sandbox = '/mnt/mesos/sandbox'
    else:
        mesos_sandbox = '$MESOS_SANDBOX'

    # wait for 'nodetool status' to reflect the replacement:
    def fun():
        stdout = cmd.run_cli(
            'task exec {} /bin/bash -c "cd {} && JAVA_HOME=$(ls -d jre*/) apache-cassandra-*/bin/nodetool -p 7199 status"'.format(task_id, mesos_sandbox))
        up_ips = []
        for line in stdout.split('\n'):
            words = list(filter(None, line.split()))
            if len(words) < 2:
                continue
            if not 'UN' == words[0]:
                continue
            up_ips.append(words[1])
        utils.out('UN nodes (want {} entries without {}): {}'.format(DEFAULT_TASK_COUNT, pod_host, up_ips))
        return len(up_ips) == DEFAULT_TASK_COUNT and not pod_host in up_ips
    # observed to take 2-3mins in practice:
    shakedown.wait_for(lambda: fun(), timeout_seconds=DEFAULT_CASSANDRA_TIMEOUT, sleep_seconds=15, noisy=True)


@pytest.mark.sanity
@pytest.mark.recovery
@pytest.mark.shutdown_node
def test_shutdown_host_test():
    scheduler_ip = shakedown.get_service_ips('marathon', PACKAGE_NAME).pop()
    utils.out('marathon ip = {}'.format(scheduler_ip))

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
    utils.out('pod name = {}, node_ip = {}, agent = {}'.format(pod_name, node_ip, old_agent))

    task_ids = tasks.get_task_ids(PACKAGE_NAME, pod_name)

    # instead of partitioning or reconnecting, we shut down the host permanently
    status, stdout = shakedown.run_command_on_agent(node_ip, 'sudo shutdown -h +1')
    utils.out('shutdown agent {}: [{}] {}'.format(node_ip, status, stdout))

    assert status is True

    utils.out('sleeping 100s after shutting down agent')
    time.sleep(100)

    cmd.run_cli('cassandra pods replace {}'.format(pod_name))
    tasks.check_tasks_updated(PACKAGE_NAME, pod_name, task_ids)

    # double check that all tasks are running
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)
    plan.wait_for_completed_deployment(PACKAGE_NAME)

    new_agent = get_pod_agent(pod_name)
    assert old_agent != new_agent


def get_pod_agent(pod_name):
    stdout = cmd.run_cli('cassandra pods info {}'.format(pod_name), print_output=False)
    return json.loads(stdout)[0]['info']['slaveId']['value']


def get_pod_host(pod_name):
    stdout = cmd.run_cli('cassandra pods info {}'.format(pod_name), print_output=False)
    labels = json.loads(stdout)[0]['info']['labels']['labels']
    for i in range(0, len(labels)):
        if labels[i]['key'] == 'offer_hostname':
            return labels[i]['value']
    return None
