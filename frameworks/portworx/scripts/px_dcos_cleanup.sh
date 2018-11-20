#!/bin/bash

# NOTE: This script is place holder script, there are improvements needed in this script.
# Specially error checks. And action based on errors.

# Script to cleanup portworx installation at dcos.
# Please run this script only after uninstalling portworx service from dcos UI.
# This script can be improved by adding error checks

echo "Shutting down inactive services"
echo "---------------------------------------------------------------------------";

px_services=`dcos service --inactive | grep -v "ACTIVE  TASKS  CPU" | grep "portworx" | awk '{print $7}'`

for SERVICE_NAME in $px_services;
do
	echo "dcos service shutdown $SERVICE_NAME;"
	dcos service shutdown $SERVICE_NAME;
done

PRE_RESERVED_ROLE="" # Set this if you started the service with a pre-reserved-role
echo "Doing Janitor cleanup for completed services"
echo "---------------------------------------------------------------------------";
for SERVICE_NAME in `dcos service --completed | awk '{print $1}'| uniq | grep -v NAME`;
do
	dcos node ssh --master-proxy --user=vagrant --leader "docker run mesosphere/janitor /janitor.py -p ${SERVICE_NAME}-principal -z dcos-service-${SERVICE_NAME}";
done

# ======================= portworx specific cleanups ====================

ips=(`dcos node --json | jq -r '.[] | select(.type == "agent") | .id'`)

# Stop the portworx service and remove the docker container
echo "---------------------------------------------------------------------------";
echo "Stoping portworx service and removing the docker container...";
for ip in "${ips[@]}"
do
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo systemctl stop portworx'
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo docker rm portworx.service -f'
done

# Remove the portworx service files
echo "---------------------------------------------------------------------------";
echo "Removing the portworx service files"
for ip in "${ips[@]}"
do
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo rm -f /etc/systemd/system/portworx.service'
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo rm -f /etc/systemd/system/dcos.target.wants/portworx.service'
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo rm -f /etc/systemd/system/multi-user.target.wants/portworx.service'
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo systemctl daemon-reload'
done

# Use with care since this will wipe data from all the disks given to Portworx
echo "---------------------------------------------------------------------------";
echo "Wipe data from all the disks given to Portworx"
for ip in "${ips[@]}"
do
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo /opt/pwx/bin/pxctl service node-wipe --all'
done

# Remove the Portworx config and files from all the nodes
# Also remove the Portworx kernel module
echo "---------------------------------------------------------------------------";
echo "Removing the Portworx config and files and Portworx kernel module"
for ip in "${ips[@]}"
do
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo chattr -i /etc/pwx/.private.json'
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo rm -rf /etc/pwx'
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo umount /opt/pwx/oci'
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo rm -rf /opt/pwx'
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant 'sudo rmmod px -f'
done
# TODO The return value of cleanup script should be based on whethe
# cleanup of portworx was successful or not.
exit 0;
