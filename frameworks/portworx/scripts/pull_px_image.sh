#!/bin/bash
# Download provided px image on all nodes.

ips=(`dcos node --json | jq -r '.[] | select(.type == "agent") | .id'`)

echo "Downloading px image $1 on all  dcos nodes...";
for ip in "${ips[@]}"
do
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant "sudo docker login -u $2 -p $3" --option StrictHostKeyChecking=no
        dcos node ssh --mesos-id=${ip} --master-proxy --user=vagrant "sudo docker pull $1" --option StrictHostKeyChecking=no
done
