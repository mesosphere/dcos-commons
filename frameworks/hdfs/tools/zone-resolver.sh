#!/bin/bash

# For rack-awareness, HDFS expects a class or a script to resolve IP addresses
# of data nodes to rack information. This script provides that resolution.
# The first and only argument this script expects ($1) is the IP address
# of the data node trying to register with the name node.
export DATA_NODE_ZONE=$(curl $SCHEDULER_API_HOSTNAME/v1/state/zone/data/$1)
echo "/$DATA_NODE_ZONE"
