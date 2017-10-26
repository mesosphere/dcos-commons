#!/bin/bash

set -e

# Configure CLI
echo "Configuring dcos cli for cluster: $CLUSTER_URL"
/build/tools/dcos_login.py

echo "Successfully configured dcos cli"

if [ -f /ssh/key ]; then
  eval "$(ssh-agent -s)"
  ssh-add /ssh/key
fi

if [ "$1" == "deploy" ]; then
  python3 testing/kdc.py deploy $PRINCIPALS_FILE_NAME
elif [ "$1" == "teardown" ]; then
  python3 testing/kdc.py teardown
fi
