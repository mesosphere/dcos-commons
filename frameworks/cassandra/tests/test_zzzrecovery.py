# NOTE: THIS FILE IS INTENTIONALLY NAMED TO BE RUN LAST. SEE test_shutdown_host().

import logging
import pprint
import pytest

import sdk_cmd
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

    # start replace and wait for it to finish
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace {}'.format(pod_to_replace))
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
    marathon_config['env']['PLACEMENT_CONSTRAINT'] = '[["hostname", "UNLIKE", "{}"]]'.format(pod_host)
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)

    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # start replace and wait for it to finish
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace {}'.format(pod_to_replace))
    sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME, timeout_seconds=RECOVERY_TIMEOUT_SECONDS)


# @@@@@@@
# WARNING: THIS MUST BE THE LAST TEST IN THIS FILE. ANY TEST THAT FOLLOWS WILL BE FLAKY.
# @@@@@@@
@pytest.mark.sanity
def test_shutdown_host():
    # Print a dump of current tasks in the cluster (and what agents they're on)
    sdk_cmd.run_cli('task')

    replace_pod = get_pod_to_replace()
    assert replace_pod is not None, 'Could not find a node to shut down'

    # Instead of partitioning or reconnecting, we shut down the host permanently
    sdk_cmd.shutdown_agent(replace_pod['host'])

    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME,
                    'pod replace {}'.format(replace_pod['name']))
    sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)

    # Another dump of current cluster tasks, now that repair has started.
    sdk_cmd.run_cli('task')

    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)
    sdk_tasks.check_running(config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)

    # One last task dump for good measure.
    sdk_cmd.run_cli('task')

    new_agent = get_pod_agent(replace_pod['name'])
    log.info('Checking that the original pod has moved to a new agent:\n'
             'old_pod={}\nnew_agent={}'.format(replace_pod, new_agent))
    assert replace_pod['agent'] != new_agent


def get_pod_to_replace():
    '''Avoid also killing the system that the scheduler is on. This is just to speed up testing.
    In practice, the scheduler would eventually get relaunched on a different node by Marathon and
    we'd be able to proceed with repairing the service from there. However, it takes 5-20 minutes
    for Mesos to decide that the agent is dead. This is also why we perform a manual 'ls' check to
    verify the host is down, rather than waiting for Mesos to tell us.
    '''
    scheduler_ip = shakedown.get_service_ips('marathon', config.SERVICE_NAME).pop()
    log.info('Scheduler IP: {}'.format(scheduler_ip))

    pods = {}
    for pod_id in range(0, config.DEFAULT_TASK_COUNT):
        pod_name = 'node-{}'.format(pod_id)
        pods[pod_name] = {
            'name': pod_name,
            'host': get_pod_host(pod_name),
            'agent': get_pod_agent(pod_name)}
    log.info('Pods:\n{}'.format(pprint.pformat(pods)))

    replace_pod = None
    for key, value in pods.items():
        if value['host'] != scheduler_ip:
            replace_pod = value
            log.info('Found pod avoiding scheduler at {}: {}'.format(scheduler_ip, value))
            break
    return replace_pod


def get_pod_agent(pod_name):
    stdout = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info {}'.format(pod_name), print_output=False, json=True)
    return get_server_info(pod_name)['info']['slaveId']['value']


def get_pod_host(pod_name):
    labels = get_server_info(pod_name)['info']['labels']['labels']
    for i in range(0, len(labels)):
        if labels[i]['key'] == 'offer_hostname':
            return labels[i]['value']
    return None


def get_server_info(pod_name):
    stdout = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info {}'.format(pod_name), print_output=False, json=True)
    for task in stdout:
        if 'server' in task['info']['name']:
            return task
