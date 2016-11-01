#!/usr/bin/env bash

# Exit immediately on errors -- the helper scripts all emit github statuses internally
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# Grab dcos-commons build/release tools:
rm -rf dcos-commons-tools/ && curl https://infinity-artifacts.s3.amazonaws.com/dcos-commons-tools.tgz | tar xz

# Get a CCM cluster if not already configured (see available settings in dcos-commons/tools/README.md):
if [ -z "$CLUSTER_URL" ]; then
    echo "CLUSTER_URL is empty/unset, launching new cluster."
    export CCM_AGENTS=5
    CLUSTER_INFO=$(./dcos-commons-tools/launch_ccm_cluster.py)
    echo "Launched cluster: ${CLUSTER_INFO}"
    export CLUSTER_URL=$(echo "${CLUSTER_INFO}" | jq .url)
    export CLUSTER_ID=$(echo "${CLUSTER_INFO}" | jq .id)
    export CLUSTER_AUTH_TOKEN=$(echo "${CLUSTER_INFO}" | jq .auth_token)
else
    echo "Using provided CLUSTER_URL as cluster: $CLUSTER_URL"
fi

echo Security: $SECURITY

if [ "$SECURITY" -eq "strict"  ]; then
    ${REPO_ROOT_DIR}/dcos-commons-tools/setup_permissions.sh nobody hdfs-role
fi

# Run shakedown tests:
${REPO_ROOT_DIR}/dcos-commons-tools/run_tests.py shakedown ${REPO_ROOT_DIR}/integration/tests/ ${REPO_ROOT_DIR}/integration/requirements.txt

# Tests succeeded. Out of courtesy, trigger a teardown of the cluster if we created it ourselves.
# Don't wait for the cluster to complete teardown.
if [ -n "${CLUSTER_ID}" ]; then
    ./dcos-commons-tools/launch_ccm_cluster.py trigger-stop ${CLUSTER_ID}
fi
