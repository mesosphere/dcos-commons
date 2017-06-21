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

    # Install replace verification job with correct node IP templated
    # (the job checks for that IP's absence in the peers list and also verifies
    # that the expected number of peers is present, meaning that the node was
    # replaced from Cassandra's perspective)
    # Note: Task will sometimes flake out because the node list can take a minute or two to update.
    #       Therefore this job has restart.policy=ON_FAILURE
    verify_replace_job = get_verify_node_replace_job(pod_host)
    with jobs.InstallJobContext([verify_replace_job]):
        jobs.run_job(verify_replace_job)


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
