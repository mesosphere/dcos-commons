import json
import pytest
import time

import shakedown

from tests.config import *
import sdk_cmd as cmd
import sdk_install as install
import sdk_marathon as marathon
import sdk_plan as plan
import sdk_tasks as tasks
import sdk_utils as utils


VERIFY_REPLACE_JOB = 'verify-node-replace'


def setup_module(module):
    install.uninstall(PACKAGE_NAME)
    utils.gc_frameworks()

    # check_suppression=False due to https://jira.mesosphere.com/browse/CASSANDRA-568
    install.install(PACKAGE_NAME, DEFAULT_TASK_COUNT, check_suppression=False)


def setup_function(function):
    tasks.check_running(PACKAGE_NAME, DEFAULT_TASK_COUNT)


def teardown_module(module):
    install.uninstall(PACKAGE_NAME)


def try_job(job_name):
    job_name = qualified_job_name(job_name)

    run_id = launch_job(job_name)
    verify_job_finished(job_name, run_id)

    return run_id in [
        r['id'] for r in
        get_runs(job_name)['history']['successfulFinishedRuns']
    ]


@pytest.mark.sanity
def test_node_replace_replaces_node():
    tasks = cmd.run_cli('task')
    node_ip = [
        t for t in tasks.split('\n') if t.startswith('node-2-server')
    ].pop().split()[1]

    # Update the placement constraints so the new node doesn't end up on the
    # same host
    config = marathon.get_config(PACKAGE_NAME)
    config['env']['PLACEMENT_CONSTRAINT'] = 'hostname:UNLIKE:{}'.format(node_ip)
    marathon.update_app(PACKAGE_NAME, config)

    plan.wait_for_completed_deployment(PACKAGE_NAME)

    # start replace and wait for it to finish
    cmd.run_cli('cassandra pods replace node-2')
    plan.wait_for_completed_recovery(PACKAGE_NAME)

    # Install replace verification job with correct node IP templated
    # (the job checks for that IP's absence in the peers list and also verifies
    # that the expected number of peers is present, meaning that the node was
    # replaced from Cassandra's perspective)
    with JobContext([VERIFY_REPLACE_JOB], NODE_IP=node_ip):
        shakedown.wait_for(lambda: try_job(VERIFY_REPLACE_JOB), timeout_seconds=30 * 60)


@pytest.mark.sanity
@pytest.mark.recovery
@pytest.mark.shutdown_node
def test_shutdown_host_test():
    scheduler_ip = shakedown.get_service_ips('marathon', PACKAGE_NAME).pop()
    utils.out('marathon ip = {}'.format(scheduler_ip))

    node_ip = None
    pod_name = None
    for pod_id in range(0, DEFAULT_TASK_COUNT):
        pod_host = get_pod_host(pod_id)
        if pod_host != scheduler_ip:
            node_ip = pod_host
            pod_name = 'node-{}'.format(pod_id)
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
