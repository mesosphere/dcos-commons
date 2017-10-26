#!/bin/bash

# Exit immediately on errors
set -e

# default values
ssh_path="${HOME}/.ssh/ccm.pem"
PRINCIPALS_FILE_NAME=""
PRINCIPALS_FILE_DIR=""

function usage()
{
  echo "Usage: $0 <cmd> [-p PATH]"
  echo "<cmd> Command to perform [deploy, teardown]"
  echo "-p PATH If deploying, path of file listing the principals to be added to the Kerberos Domain Controller"
  echo "Cluster must be created and \$CLUSTER_URL set"
}

if [ "$#" -lt 1 -o "$#" -gt 2 ]; then
  usage
  exit 1
fi

if [ "$#" -eq 2 ]; then
  PRINCIPALS_FILE_NAME="$2"
  PRINCIPALS_FILE_DIR="$( cd "$( dirname "$2" )" && pwd )"
fi

if [ "$1" == "deploy" ]; then
  docker run --rm \
    -e CLUSTER_URL="$CLUSTER_URL" \
    -e PRINCIPALS_FILE_NAME="$PRINCIPALS_FILE_NAME" \
    -v $(pwd):/build \
    -v $ssh_path:/ssh/key \
    -w /build \
    -t \
    -i \
    nvaziri/kdc:dev_1 \
    bash tools/kdc_controller.sh $1 $2
elif [ "$1" == "teardown" ]; then
  docker run --rm \
    -e CLUSTER_URL="$CLUSTER_URL" \
    -v $(pwd):/build \
    -w /build \
    -t \
    -i \
    nvaziri/kdc:dev_1 \
    bash tools/kdc_controller.sh $1
fi
