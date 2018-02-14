import json
import logging
import os
import time

import pytest

import sdk_cmd
import sdk_hosts
import sdk_install
import sdk_plan
import sdk_tasks
from tests import config

import dcos_launch
import dcos_test_utils

log = logging.getLogger(__name__)


@pytest.fixture(scope='session')
def mount_volumes():
    script = """
#!/bin/bash
sudo systemctl stop dcos-mesos-slave.service
sudo rm -f /var/lib/dcos/mesos-resources
sudo rm -f /var/lib/mesos/slave/meta/slaves/latest
"""
    for i in range(2):
        script += """
sudo mkdir -p /dcos/volume{idx}
sudo dd if=/dev/zero of=/root/volume{idx}.img bs=1M count={size}
sudo losetup /dev/loop{idx} /root/volume{idx}.img
sudo mkfs -t ext4 /dev/loop{idx}
sudo losetup -d /dev/loop{idx}
echo "/root/volume{idx}.img /dcos/volume{idx} auto loop 0 2" | sudo tee -a /etc/fstab
sudo mount /dcos/volume{idx}
""".format(idx=i, size=200)

    script += """
sudo systemctl restart dcos-mesos-slave.service
"""

    cluster_info_path = os.getenv('CLUSTER_INFO_PATH', 'cluster_info.json')
    if not os.path.exists(cluster_info_path):
        assert False, 'No cluster info to work with!!'
    cluster_info_json = json.load(open(cluster_info_path))
    launcher = dcos_launch.get_launcher(cluster_info_json)
    description = launcher.describe()
    ssh_client = launcher.get_ssh_client()
    with ssh_client.tunnel(description['masters'][0]['public_ip']) as t:
        t.copy_file(dcos_test_utils.helpers.session_tempfile(ssh_client.key), 'ssh_key')
        t.copy_file(dcos_test_utils.helpers.session_tempfile(script), 'volume_script.sh')
        t.command(['chmod', '600', 'ssh_key'])
        ssh_command = ['ssh', '-i', 'ssh_key'] + dcos_test_utils.ssh_client.SHARED_SSH_OPTS
        scp_command = ['scp', '-i', 'ssh_key'] + dcos_test_utils.ssh_client.SHARED_SSH_OPTS
        for private_agent in description['private_agents']:
            target = '{}@{}'.format(ssh_client.user, private_agent['private_ip'])
            t.command(scp_command + ['volume_script.sh', target + ':~/volume_script.sh'])
            t.command(ssh_command + [target, 'bash', 'volume_script.sh'])
        # nasty hack until we add a better post-flight
        time.sleep(60)


@pytest.fixture(scope='module', autouse=True)
def configure_package(configure_security):
    try:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)
        options = {
            "service": {
                "spec_file": "examples/pod-mount-volume.yml"
            }
        }

        sdk_install.install(config.PACKAGE_NAME, config.SERVICE_NAME, 2, additional_options=options)

        yield # let the test session execute
    finally:
        sdk_install.uninstall(config.PACKAGE_NAME, config.SERVICE_NAME)


@pytest.mark.sanity
def test_kill_node(mount_volumes):
    '''kill the node task, verify that the node task is relaunched against the same executor as before'''
    verify_shared_executor('hello-0')

    old_node_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-0-node')
    assert len(old_node_ids) == 1
    old_agent_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-0-agent')
    assert len(old_agent_ids) == 1

    sdk_cmd.kill_task_with_pattern(
        'node-container-path/output', # hardcoded in cmd, see yml
        sdk_hosts.system_host(config.SERVICE_NAME, 'hello-0-node'))

    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello-0-node', old_node_ids)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)
    sdk_tasks.check_tasks_not_updated(config.SERVICE_NAME, 'hello-0-agent', old_agent_ids)

    # the first verify_shared_executor call deleted the files. only the nonessential file came back via its relaunch.
    verify_shared_executor('hello-0')


@pytest.mark.sanity
def test_kill_agent(mount_volumes):
    '''kill the agent task, verify that the agent task is relaunched against the same executor as before'''
    verify_shared_executor('hello-0')

    old_node_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-0-node')
    assert len(old_node_ids) == 1
    old_agent_ids = sdk_tasks.get_task_ids(config.SERVICE_NAME, 'hello-0-agent')
    assert len(old_agent_ids) == 1

    sdk_cmd.kill_task_with_pattern(
        'agent-container-path/output', # hardcoded in cmd, see yml
        sdk_hosts.system_host(config.SERVICE_NAME, 'hello-0-agent'))

    sdk_tasks.check_tasks_not_updated(config.SERVICE_NAME, 'hello-0-node', old_node_ids)
    sdk_plan.wait_for_completed_recovery(config.SERVICE_NAME)
    sdk_tasks.check_tasks_updated(config.SERVICE_NAME, 'hello-0-agent', old_agent_ids)

    # the first verify_shared_executor call deleted the files. only the nonessential file came back via its relaunch.
    verify_shared_executor('hello-0')


def verify_shared_executor(pod_name):
    '''verify that both tasks share the same executor:
    - matching ExecutorInfo
    - both 'essential' and 'nonessential' present in shared-volume/ across both tasks
    '''
    tasks = sdk_cmd.svc_cli(config.PACKAGE_NAME, config.SERVICE_NAME, 'pod info {}'.format(pod_name), json=True)
    assert len(tasks) == 2

    # check that the task executors all match
    executor = tasks[0]['info']['executor']
    for task in tasks[1:]:
        assert executor == task['info']['executor']
