#!/bin/bash

#
# This script runs dcos-tests against build artifacts which had been uploaded with ci-build.sh
#
# THIS SCRIPT IS THE ENTRYPOINT FOR RUNNING DCOS-TESTS AGAINST BUILD ARTIFACTS
#
# When running this script, the user must provide the following args:
# 1) The path of the tests to be run. Eg "infinitytests/kafka".
# 2) (optional) The type of tests to run. Eg "recovery". Defaults to "sanity".
#

# Paths:
BUILD_SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
DCOS_TESTS_ROOT="${BUILD_SCRIPT_DIR}/.."

syntax() {
    echo "Syntax: $0 <test/dir> [test_type]"
    echo "  Example: $ FRAMEWORK_TESTS_CHANNEL=testing/custom $0 infinitytests/kafka sanity"
}
run_cmd() {
    echo "$@"
    $@
}

# automatically falls back to printing if we're not in CI:
notify_github() {
    run_cmd ${BUILD_SCRIPT_DIR}/github_update.py $1 $2 $3
}

# Validate required env is present
if [ -z "$FRAMEWORK_TESTS_CHANNEL" ]; then
    export FRAMEWORK_TESTS_CHANNEL="testing/master"
fi
if [ -z "$GITHUB_TEST_TYPE" ]; then
    GITHUB_TEST_TYPE="test:$FRAMEWORK_TESTS_CHANNEL"
fi

TEST_DIR=$1
TEST_TYPE=$2
# Validate args were all present
if [ -z "$TEST_DIR" ]; then
    notify_github error $GITHUB_TEST_TYPE "Missing arguments to ci-test.sh"
    syntax
    exit 2
fi

# Normalize paths: Remove trailing '/'s if present
case "$TEST_DIR" in
    */)
        TEST_DIR="${TEST_DIR%?}"
        ;;
    *)
        TEST_DIR="$TEST_DIR"
        ;;
esac

# GO INTO DCOS-TESTS/ DIRECTORY FOR THE REST OF THIS SCRIPT
# Many of the python tools below assume we're there
cd $DCOS_TESTS_ROOT

notify_github pending $GITHUB_TEST_TYPE "Initializing environment for $TEST_TYPE tests in dcos-tests/$TEST_DIR"

if [ ! -d "$TEST_DIR" ]; then
    echo "Invalid test location (relative to $(pwd)): $TEST_DIR"
    ls -l
    notify_github error $GITHUB_TEST_TYPE "Invalid path to tests within dcos-tests/: $TEST_DIR"
    exit 3
fi

echo "### Set up python env..."
source utils/python_setup
if [ $? -ne 0 ]; then
    echo "Env setup failed, exiting"
    notify_github error $GITHUB_TEST_TYPE "Failed to create python environment"
    exit 4
fi

notify_github pending $GITHUB_TEST_TYPE "Creating cluster for $TEST_DIR $TEST_TYPE tests"

echo "### Create cluster ($FRAMEWORK_TESTS_CHANNEL)..."
PACKAGE_DASH_DIR=$(echo $TEST_DIR | sed "s,/,-,g") # "/" => "-"
export FRAMEWORK_TESTS_STACK_PREFIX="ci-${PACKAGE_DASH_DIR}-"
export FRAMEWORK_TESTS_CLUSTER_DESC="CI tests for ${TEST_DIR}"
if [ -z "$FRAMEWORK_TESTS_CLOUDFORMATION_TEMPLATE" ]; then
    # default to EE if not already specified
    export FRAMEWORK_TESTS_CLOUDFORMATION_TEMPLATE="ee.single-master.cloudformation.json"
fi
# See framework_test_utils.py for other settings
# hack to get around teamcity aborting the script if bringup fails:
TEST_RESULT="success"
python -u launch_dcos_cluster.py || TEST_RESULT="failure"
if [ "$TEST_RESULT" = "failure" ]; then
    echo "Cluster launch failed: exiting early"
    notify_github error $GITHUB_TEST_TYPE "Failed to launch DCOS cluster"
    exit 5
fi
echo "### Cluster info: $(cat launch-data)"
export PYTHONPATH=$(pwd)
echo "https://$(jq -r '.master_dns_address' launch-data)" > docker-context/dcos-url.txt
echo "    => Master URI for tests: $(cat docker-context/dcos-url.txt)"

if [ "$OPTIONS_JSON_FILE" ]; then
  export STACK_ID=$(jq -r '.stack_id' launch-data)
  python -u AutoMountMan.py || TEST_RESULT="failure"
  if [ "$TEST_RESULT" = "failure" ]; then
    echo "Failed to configure MOUNT volumes. Launch failed! Exiting early!"
    notify_github error $GITHUB_TEST_TYPE "Failed to launch DCOS cluster"
    exit 5
  fi
fi

notify_github pending $GITHUB_TEST_TYPE "Building dcos-cli image for $TEST_DIR $TEST_TYPE tests"

echo "### Build dcos-cli docker image..."
# hack to get around teamcity aborting the script if setup fails:
docker build -t mesosphere/dcos-cli docker-context/ || TEST_RESULT="failure"
if [ "$TEST_RESULT" = "failure" ]; then
    echo "dcos-cli Docker creation failed, exiting"
    notify_github error $GITHUB_TEST_TYPE "Failed to create dcos-cli docker image"
    exit 6
fi

notify_github pending $GITHUB_TEST_TYPE "Running $TEST_TYPE tests in dcos-tests/$TEST_DIR"

echo "### Run $TEST_TYPE tests in $TEST_DIR ..."
if [ -z "${TEST_REPORT_FILE}" ]; then
    TEST_REPORT_FILE="result.xml"
fi
echo "Writing results to ${TEST_REPORT_FILE}"

# hack to get around teamcity aborting the script if tests fail:
if [ -z "$TEST_TYPE" ]; then
  py.test --junitxml=${TEST_REPORT_FILE} -vv -s -m 'sanity or recovery' $TEST_DIR || TEST_RESULT="failure"
else
  py.test --junitxml=${TEST_REPORT_FILE} -vv -s -m $TEST_TYPE $TEST_DIR || TEST_RESULT="failure"
fi

if [ "$TEST_RESULT" = "failure" ]; then
    echo "### Tests failed"
    notify_github failure $GITHUB_TEST_TYPE "$TEST_TYPE tests in dcos-tests/$TEST_DIR failed"
    # Don't tear down the cluster, leave a window for diagnosing the failure via cluster logs
    echo "### Skipping cluster teardown to allow diagnosis"
    exit 7
fi

echo "### Delete cluster..."
# hack to get around teamcity aborting the script if cluster deletion failed:
python -u delete_dcos_cluster.py || true
if [ $? -ne 0 ]; then
    echo "### Cluster deletion failed, but returning success anyway"
fi

notify_github success $GITHUB_TEST_TYPE "$TEST_TYPE tests in dcos-tests/$TEST_DIR succeded"
