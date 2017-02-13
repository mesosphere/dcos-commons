#!/usr/bin/env bash

# This file contains logic for integration tests which are executed by CI upon pull requests to
# dcos-commons. The script builds the Hello World framework, packages and uploads it, then runs its
# integration tests against a newly-launched cluster.

# Exit immediately on errors -- the helper scripts all emit github statuses internally
set -e

function proxylite_preflight {
    bash frameworks/proxylite/scripts/ci.sh pre-test
}

function run_framework_tests {
    framework=$1
    FRAMEWORK_DIR=${REPO_ROOT_DIR}/frameworks/$framework

    if [ "$framework" = "proxylite" ]; then
        if ! proxylite_preflight; then
            sleep 5
            proxylite_preflight
        fi
    fi

    # Build/upload framework scheduler artifact if one is not directly provided:
    if [ -z "${!STUB_UNIVERSE_URL}" ]; then
        STUB_UNIVERSE_URL=$(echo "${framework}_STUB_UNIVERSE_URL" | awk '{print toupper($0)}')
        # Build/upload framework scheduler:
        UNIVERSE_URL_PATH=${REPO_ROOT_DIR}/frameworks/$framework/${framework}-universe-url
        UNIVERSE_URL_PATH=$UNIVERSE_URL_PATH ${FRAMEWORK_DIR}/build.sh aws

        if [ ! -f "$UNIVERSE_URL_PATH" ]; then
            echo "Missing universe URL file: $UNIVERSE_URL_PATH"
            exit 1
        fi
        export STUB_UNIVERSE_URL=$(cat $UNIVERSE_URL_PATH)
        rm -f $UNIVERSE_URL_PATH
        echo "Built/uploaded stub universe: $STUB_UNIVERSE_URL"
    else
        echo "Using provided STUB_UNIVERSE_URL: $STUB_UNIVERSE_URL"
    fi

    # Run shakedown tests in framework scheduler directory:
    ${REPO_ROOT_DIR}/tools/run_tests.py shakedown ${FRAMEWORK_DIR}/tests/
}

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

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

# A specific framework can be specified to run its tests
# Otherwise all tests are ran
if [ -n "$1" ]; then
    run_framework_tests $1
else
    for framework in $(ls $REPO_ROOT_DIR/frameworks); do
        if [ "$framework" = "kafka" ]; then # no tests exists for Kafka as of writing this
            continue
        fi
        run_framework_tests $framework
    done
fi

# Tests succeeded. Out of courtesy, trigger a teardown of the cluster if we created it ourselves.
# Don't wait for the cluster to complete teardown.
if [ -n "${CLUSTER_ID}" ]; then
    ${REPO_ROOT_DIR}/tools/launch_ccm_cluster.py trigger-stop ${CLUSTER_ID}
fi
