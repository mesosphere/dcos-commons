#!/usr/bin/env bash

# This file contains logic for integration tests which are executed by CI upon pull requests to
# dcos-commons. As such this focuses on executing tests for the Hello World Scheduler.
# Individual projects/examples within the repository may have their own test scripts for exercising
# additional custom functionality.

# Exit immediately on errors -- the helper scripts all emit github statuses internally
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# Path to hello world scheduler:
HELLOWORLD_DIR=${REPO_ROOT_DIR}/frameworks/helloworld

# Build/upload hello world scheduler artifact if one is not directly provided:
if [ -z "$STUB_UNIVERSE_URL" ]; then
    # Build/upload hello world scheduler:
    UNIVERSE_URL_FILE=${REPO_ROOT_DIR}/helloworld-universe-url
    UNIVERSE_URL_FILE=$UNIVERSE_URL_FILE ${HELLOWORLD_DIR}/build.sh aws
    if [ ! -f "${UNIVERSE_URL_FILE}" ]; then
        echo "Missing universe URL file: $UNIVERSE_URL_FILE"
        exit 1
    fi
    export STUB_UNIVERSE_URL=$(cat $UNIVERSE_URL_FILE)
    rm -f $UNIVERSE_URL_FILE
    echo "Built/uploaded stub universe: $STUB_UNIVERSE_URL"
else
    echo "Using provided STUB_UNIVERSE_URL: $STUB_UNIVERSE_URL"
fi

# Get a CCM cluster if not already configured (see available settings in dcos-commons/tools/README.md):
if [ -z "$CLUSTER_URL" ]; then
    echo "CLUSTER_URL is empty/unset, launching new cluster."
    export CCM_AGENTS=5
    CLUSTER_INFO=$(${REPO_ROOT_DIR}/tools/launch_ccm_cluster.py)
    echo "Launched cluster: ${CLUSTER_INFO}"
    export CLUSTER_URL=$(echo "${CLUSTER_INFO}" | jq .url)
    export CLUSTER_ID=$(echo "${CLUSTER_INFO}" | jq .id)
    export CLUSTER_AUTH_TOKEN=$(echo "${CLUSTER_INFO}" | jq .auth_token)
else
    echo "Using provided CLUSTER_URL as cluster: $CLUSTER_URL"
fi

# Run shakedown tests in helloworld scheduler directory:
${REPO_ROOT_DIR}/tools/run_tests.py \
                shakedown \
                ${HELLOWORLD_DIR}/integration/tests/ \
                ${HELLOWORLD_DIR}/integration/requirements.txt

# Tests succeeded. Out of courtesy, trigger a teardown of the cluster if we created it ourselves.
# Don't wait for the cluster to complete teardown.
if [ -n "${CLUSTER_ID}" ]; then
    ${REPO_ROOT_DIR}/tools/launch_ccm_cluster.py trigger-stop ${CLUSTER_ID}
fi
