#!/usr/bin/env bash

# This file contains logic for integration tests which are executed by CI upon pull requests to
# dcos-commons. As such this focuses on executing tests for the Reference Scheduler.
# Individual projects/examples within the repository may have their own test scripts for exercising
# additional custom functionality.

# Exit immediately on errors -- the helper scripts all emit github statuses internally
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# Path to reference scheduler:
REFERENCE_DIR=${REPO_ROOT_DIR}/examples/reference

# Build/upload reference scheduler artifact if one is not directly provided:
if [ -z "$STUB_UNIVERSE_URL" ]; then
    # Build/upload reference scheduler:
    ${REFERENCE_DIR}/build.sh | tee ${REPO_ROOT_DIR}/reference-build-output
    export STUB_UNIVERSE_URL=$(tail -n 1 ${REPO_ROOT_DIR}/reference-build-output)
    rm -f ${REPO_ROOT_DIR}/reference-build-output
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

# Run shakedown tests in reference scheduler directory:
${REPO_ROOT_DIR}/tools/run_tests.py \
                shakedown \
                ${REFERENCE_DIR}/integration/tests/ \
                ${REFERENCE_DIR}/integration/requirements.txt

# Tests succeeded. Out of courtesy, trigger a teardown of the cluster if we created it ourselves.
# Don't wait for the cluster to complete teardown.
if [ -n "${CLUSTER_ID}" ]; then
    ${REPO_ROOT_DIR}/tools/launch_ccm_cluster.py trigger-stop ${CLUSTER_ID}
fi
