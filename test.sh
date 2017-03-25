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

# Get a CCM cluster if not already configured (see available settings in dcos-commons/tools/README.md):
if [ -z "$CLUSTER_URL" ]; then
    if [ "$SECURITY" = "strict" ]; then
        # in launch_ccm_cluster, for strict, we run
        # tools/create_service_account.sh which depends on the dcos binary.
        if ! which dcos >/dev/null 2>&1; then
            echo "Will fetch dcos binary for use by strict mode cluster setup"
            dcos_cli_bindir="$REPO_ROOT_DIR"/tmp/dcos_bin
            if [ -d $dcos_cli_bindir ]; then
                echo "Wiping prior dcos bin dir $dcos_cli_bindir"
                rm -rf "$dcos_cli_bindir"
            fi
            mkdir -p "$dcos_cli_bindir"
            echo "curl https://downloads.dcos.io/binaries/cli/linux/x86-64/dcos-1.8/dcos --output $dcos_cli_bindir/dcos"
            curl https://downloads.dcos.io/binaries/cli/linux/x86-64/dcos-1.8/dcos --output "$dcos_cli_bindir/dcos"
            chmod a+x "$dcos_cli_bindir/dcos"
            export PATH="$PATH":"$dcos_cli_bindir"
        fi
    fi

    echo "CLUSTER_URL is empty/unset, launching new cluster."
    export CCM_AGENTS=5
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
