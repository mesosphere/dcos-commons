#!/usr/bin/env python3
import json
import os
import time

import dcos_launch
from dcos_test_utils import logger, helpers, ssh_client


def mount_volumes():
    """ Will create 200MB partions on clusters launched by dcos-launch
    """
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
        raise Exception('No cluster info to work with!')
    cluster_info_json = json.load(open(cluster_info_path))
    launcher = dcos_launch.get_launcher(cluster_info_json)
    description = launcher.describe()
    ssh = launcher.get_ssh_client()
    with ssh.tunnel(description['masters'][0]['public_ip']) as t:
        t.copy_file(helpers.session_tempfile(ssh.key), 'ssh_key')
        t.copy_file(helpers.session_tempfile(script), 'volume_script.sh')
        t.command(['chmod', '600', 'ssh_key'])
        ssh_command = ['ssh', '-i', 'ssh_key'] + ssh_client.SHARED_SSH_OPTS
        scp_command = ['scp', '-i', 'ssh_key'] + ssh_client.SHARED_SSH_OPTS
        for private_agent in description['private_agents']:
            target = '{}@{}'.format(ssh.user, private_agent['private_ip'])
            t.command(scp_command + ['volume_script.sh', target + ':~/volume_script.sh'])
            t.command(ssh_command + [target, 'bash', 'volume_script.sh'])
        # nasty hack until we add a better post-flight
        time.sleep(60)


if __name__ == '__main__':
    logger.setup(os.getenv('LOG_LEVEL', 'DEBUG'))
    mount_volumes()
