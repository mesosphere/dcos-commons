#!/bin/bash

export DATA_NODE_ZONE=$(curl $SCHEDULER_API_HOSTNAME/v1/state/zone/data/$1)
echo "/$DATA_NODE_ZONE"