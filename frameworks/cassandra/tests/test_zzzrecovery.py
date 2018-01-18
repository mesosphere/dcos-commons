# NOTE: THIS FILE IS INTENTIONALLY NAMED TO BE RUN LAST. SEE test_shutdown_host().

import logging
import pprint
import pytest
import re

import sdk_cmd
import sdk_install
import sdk_marathon
import sdk_plan
import sdk_tasks
import shakedown
from tests import config

RECOVERY_TIMEOUT_SECONDS = 20 * 60
SERVER_NAME_PATTERN = re.compile('^node-[0-9]+-server$')

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
@pytest.mark.dcos_min_version('1.9', reason='dcos task exec not supported < 1.9')
def test_node_replace_replaces_node():
    replace_task = [
        task for task in sdk_tasks.get_summary()
        if task.name == 'node-2-server'][0]
    log.info('avoid host for task {}'.format(replace_task))

    replace_pod_name = replace_task.name[:-len('-server')]

    # Update the placement constraints so the new node doesn't end up on the same host
    marathon_config = sdk_marathon.get_config(config.SERVICE_NAME)
    marathon_config['env']['PLACEMENT_CONSTRAINT'] = '[["hostname", "UNLIKE", "{}"]]'.format(replace_task.host)
    sdk_marathon.update_app(config.SERVICE_NAME, marathon_config)

    sdk_plan.wait_for_completed_deployment(config.SERVICE_NAME)

    # start replace and wait for it to finish
    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod replace {}'.format(replace_pod_name))
    sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME, timeout_seconds=RECOVERY_TIMEOUT_SECONDS)


# @@@@@@@
# WARNING: THIS MUST BE THE LAST TEST IN THIS FILE. ANY TEST THAT FOLLOWS WILL BE FLAKY.
# @@@@@@@
@pytest.mark.sanity
def test_shutdown_host():
    replace_task = sdk_tasks.get_task_avoiding_scheduler(config.SERVICE_NAME, SERVER_NAME_PATTERN)
    assert replace_task is not None, 'Could not find a node to shut down'
    replace_pod_name = replace_task.name[:-len('-server')]

    # Instead of partitioning or reconnecting, we shut down the host permanently
    sdk_cmd.shutdown_agent(replace_task.host)

    sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME,
                    'pod replace {}'.format(replace_pod_name))
    sdk_plan.wait_for_kicked_off_recovery(config.SERVICE_NAME)

    # Print another dump of current cluster tasks, now that repair has started.
    sdk_tasks.get_summary()

    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)
    sdk_tasks.check_running(config.SERVICE_NAME, config.DEFAULT_TASK_COUNT)

    new_task = [
        task for task in sdk_tasks.get_summary()
        if task.name == replace_task.name][0]
    log.info('Checking that the original pod has moved to a new agent:\n'
             'old={}\nnew={}'.format(replace_task, new_task))
    assert replace_task.agent != new_task.agent
