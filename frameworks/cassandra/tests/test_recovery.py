import logging
import tempfile
import time

import pytest
import sdk_cmd as cmd
import sdk_install
import sdk_jobs
import sdk_marathon
import sdk_plan
import sdk_tasks
import sdk_utils
import shakedown
from tests import config

RECOVERY_TIMEOUT_SECONDS = 20 * 60

log = logging.getLogger(__name__)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        test_jobs = config.get_all_jobs()
        # destroy any leftover jobs first, so that they don't touch the newly installed service:
        for job in test_jobs:
            sdk_jobs.remove_job(job)

        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)

        tmp_dir = tempfile.mkdtemp(prefix='cassandra-test')
        for job in test_jobs:
            sdk_jobs.install_job(job, tmp_dir=tmp_dir)

        with sdk_jobs.RunJobContext(
            before_jobs=[
                config.get_replicate_system_traces_job(),
                config.get_replicate_system_auth_job(),
                config.get_replicate_system_distributed_job()
            ],
            after_jobs=[]):

            yield  # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
@pytest.mark.dcos_min_version('1.9', reason='dcos task exec not supported < 1.9')
def test_node_replace_replaces_seed_node():
    pod_to_replace = 'node-0'

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
