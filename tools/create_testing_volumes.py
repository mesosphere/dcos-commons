#!/usr/bin/env python3
import json
import os
import time

import dcos_launch
from dcos_test_utils import logger, helpers, ssh_client

# Here we create 4 MOUNT volumes on every agent, where the first two have no
# profile and their filesystem default to ext4, and the last two have the "xfs"
# profile and filesystem.
MOUNT_VOLUME_PROFILES = [None, None, "xfs", "xfs"]
MOUNT_VOLUME_SIZE_MB = 200


def mount_volumes():
    """ Will create 200MB partions on clusters launched by dcos-launch
    """
    volume_script = """#!/bin/bash
set -e

if [ {dcos_mounts} ]; then
    echo 'Volumes already exist, exiting early'
    exit 0
fi

echo 'Stopping agent and clearing state...'

systemctl stop dcos-mesos-slave.service

cat /var/lib/dcos/mesos-resources || echo 'No resources file found'
ls -l /var/lib/mesos/slave/meta/slaves/latest || echo 'No latest agent symlink found'
rm -f /var/lib/dcos/mesos-resources
rm -f /var/lib/mesos/slave/meta/slaves/latest

losetup -a
""".format(
        dcos_mounts=" -a ".join(["-e /dcos/volume{}".format(i) for i, _ in enumerate(MOUNT_VOLUME_PROFILES)])
    )

    for i, p in enumerate(MOUNT_VOLUME_PROFILES):
        volume_script += """
if [ ! -e {loop_file} ]; then
    echo 'Creating loopback device {loop_dev}...'

    dd if=/dev/zero of={loop_file} bs=1M count={size_mb}
    losetup {loop_dev} {loop_file}
    mkfs -t {fs_type} {loop_dev}
    losetup -d {loop_dev}
fi

if [ ! -e {dcos_mount} ]; then
    echo 'Creating loopback volume {dcos_mount}...'

    mkdir -p {dcos_mount}
    echo \"{loop_file} {dcos_mount} auto loop 0 2\" | tee -a /etc/fstab
    mount {dcos_mount}
fi
""".format(
            size_mb=MOUNT_VOLUME_SIZE_MB,
            dcos_mount="/dcos/volume{}".format(i),
            loop_dev="/dev/loop{}".format(i),
            loop_file="/root/volume{}.img".format(i),
            fs_type=p or "ext4"
        )

    # To create profile mount volumes, we manually run `make_disk_resources.py`
    # to generate disk resources, then parse the result and set the
    # `disk.source.profile` field for each profile mount volume.
    volume_script += """
echo 'Updating disk resources...'

export MESOS_WORK_DIR MESOS_RESOURCES
eval $(sed -E "s/^([A-Z_]+)=(.*)$/\\1='\\2'/" /opt/mesosphere/etc/mesos-slave-common)  # Set up `MESOS_WORK_DIR`.
eval $(sed -E "s/^([A-Z_]+)=(.*)$/\\1='\\2'/" /opt/mesosphere/etc/mesos-slave)         # Set up `MESOS_RESOURCES`.
/opt/mesosphere/bin/make_disk_resources.py /var/lib/dcos/mesos-resources
source /var/lib/dcos/mesos-resources
/opt/mesosphere/bin/python -c "
import json;
import os;

profiles = {profiles}
resources = json.loads(os.environ['MESOS_RESOURCES'])

for r in resources:
    try:
        disk_source = r['disk']['source']
        disk_source['profile'] = profiles[disk_source['mount']['root']]
    except KeyError:
        pass

print('MESOS_RESOURCES=\\'' + json.dumps(resources) + '\\'')
" > /var/lib/dcos/mesos-resources

echo 'Restarting agent...'

systemctl restart dcos-mesos-slave.service
""".format(profiles={"/dcos/volume{}".format(i): p for i, p in enumerate(MOUNT_VOLUME_PROFILES) if p})

    cluster_info_path = os.getenv("CLUSTER_INFO_PATH", "cluster_info.json")
    if not os.path.exists(cluster_info_path):
        raise Exception("No cluster info to work with!")
    cluster_info_json = json.load(open(cluster_info_path))
    launcher = dcos_launch.get_launcher(cluster_info_json)
    description = launcher.describe()
    ssh = launcher.get_ssh_client()
    with ssh.tunnel(description["masters"][0]["public_ip"]) as t:
        t.copy_file(helpers.session_tempfile(ssh.key), "ssh_key")
        t.copy_file(helpers.session_tempfile(volume_script), "volume_script.sh")
        t.command(["chmod", "600", "ssh_key"])
        ssh_command = ["ssh", "-i", "ssh_key"] + ssh_client.SHARED_SSH_OPTS
        scp_command = ["scp", "-i", "ssh_key"] + ssh_client.SHARED_SSH_OPTS
        for private_agent in description["private_agents"]:
            target = "{}@{}".format(ssh.user, private_agent["private_ip"])
            t.command(scp_command + ["volume_script.sh", target + ":~/volume_script.sh"])
            t.command(ssh_command + [target, "sudo", "bash", "volume_script.sh"])
        # nasty hack until we add a better post-flight
        time.sleep(60)


if __name__ == "__main__":
    logger.setup(os.getenv("LOG_LEVEL", "DEBUG"))
    mount_volumes()
