#!/usr/bin/env bash

# This file contains logic for integration tests which are executed by CI upon pull requests to
# dcos-commons. The script builds each framework, packages and uploads it, then runs its
# integration tests against a newly-launched cluster.

# Exit immediately on errors -- the helper scripts all emit github statuses internally
set -e

function proxylite_preflight {
    bash frameworks/proxylite/scripts/ci.sh pre-test
}

function run_framework_tests {
    framework=$1
    FRAMEWORK_DIR=${REPO_ROOT_DIR}/frameworks/${framework}

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
        UNIVERSE_URL_PATH=$FRAMEWORK_DIR/${framework}-universe-url
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

    echo Security: $SECURITY
    if [ "$SECURITY" = "strict" ]; then
        ${REPO_ROOT_DIR}/tools/setup_permissions.sh root ${framework}-role
        # Some tests install a second instance of a framework, such as "hdfs2"
        ${REPO_ROOT_DIR}/tools/setup_permissions.sh root ${framework}2-role
    fi

    # Run shakedown tests in framework directory:
    TEST_GITHUB_LABEL="${framework}" ${REPO_ROOT_DIR}/tools/run_tests.py shakedown ${FRAMEWORK_DIR}/tests/
}

echo "Beginning integration tests at "`date`

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

cd $REPO_ROOT_DIR

# ensure we have a dcos binary
TESTRUN_TEMPDIR=$(mktemp -d /tmp/sdktest.XXXXXXXX)
export TESTRUN_TEMPDIR
echo "test workdir set to $TESTRUN_TEMPDIR"
export PATH=$TESTRUN_TEMPDIR:$PATH

# Get a CCM cluster if not already configured (see available settings in dcos-commons/tools/README.md):
if [ -z "$CLUSTER_URL" ]; then
    echo "CLUSTER_URL is empty/unset, launching new cluster."
    export CCM_AGENTS=6
    CLUSTER_INFO=$(${REPO_ROOT_DIR}/tools/launch_ccm_cluster.py)
    echo "Launched cluster: ${CLUSTER_INFO}"
    # jq emits json strings by default: "value".  Use --raw-output to get value without quotes
    export CLUSTER_URL=$(echo "${CLUSTER_INFO}" | jq --raw-output .url)
    export CLUSTER_ID=$(echo "${CLUSTER_INFO}" | jq .id)
    export CLUSTER_AUTH_TOKEN=$(echo "${CLUSTER_INFO}" | jq --raw-output .auth_token)
    CLUSTER_CREATED="true"
else
    echo "Using provided CLUSTER_URL as cluster: $CLUSTER_URL"
    CLUSTER_CREATED=""
fi

# launch_ccm_cluster.py may have fetched already
if [ ! -f $TESTRUN_TEMPDIR/dcos ]; then
    echo "Fetching dcos cli"
    python3 ${REPO_ROOT_DIR}/tools/cli_install.py $CLUSTER_URL $TESTRUN_TEMPDIR
fi

# A specific framework can be specified to run its tests
# Otherwise all tests are run in random framework order
if [ -n "$1" ]; then
    run_framework_tests $1
else
    for framework in $(ls $REPO_ROOT_DIR/frameworks | while IFS= read -r fw; do printf "%05d %s\n" "$RANDOM" "$fw"; done | sort -n | cut -c7-); do
        echo "Starting shakedown tests for $framework at "`date`
        run_framework_tests $framework
    done
fi

# Tests succeeded. Out of courtesy, trigger a teardown of the cluster if we created it ourselves.
# Don't wait for the cluster to complete teardown.
if [ -n "${CLUSTER_CREATED}" ]; then
    ${REPO_ROOT_DIR}/tools/launch_ccm_cluster.py trigger-stop ${CLUSTER_ID}
fi
