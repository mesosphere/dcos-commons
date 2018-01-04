#!/usr/bin/env bash
set -e

# Builds the default service CLI for use by SDK-based DC/OS packages.
# Individual services may replace this default CLI with a custom CLI.
# Produces 3 artifacts: dcos-service-cli[-linux|-darwin|.exe]

SDK_CLI_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# required env for build_go_exe.sh (and also used below):
export REPO_ROOT_DIR=$(dirname $(dirname $SDK_CLI_DIR))
export REPO_NAME=$(basename $REPO_ROOT_DIR)

if [ x"${GO_TESTS:-true}" == x"true" ]; then
    # Manually run unit tests for the CLI libraries in dcos-commons/cli.
    # Reuse the GOPATH structure which was created by the build in <repo-root>/.gopath/
    export GOPATH=${REPO_ROOT_DIR}/.gopath
    CLI_LIB_DIR_IN_GOPATH=$GOPATH/src/github.com/mesosphere/${REPO_NAME}/cli
    cd $CLI_LIB_DIR_IN_GOPATH

    # Create 'vendor' symlink in dcos-commons/cli which points to dcos-commons/govendor:
    rm -f vendor
    ln -s ../govendor vendor

    # Only run 'go test' in subdirectories that actually contain *_test.go:
    TEST_DIRS=$(find . -type f -name '*_test.go' | sed -r 's|/[^/]+$||' | sort | uniq)
    for TEST_DIR in $TEST_DIRS; do
        cd $CLI_LIB_DIR_IN_GOPATH/$TEST_DIR
        go test
    done
    rm -f $CLI_LIB_DIR_IN_GOPATH/vendor
fi

cd $SDK_CLI_DIR

$REPO_ROOT_DIR/tools/build_go_exe.sh sdk/cli/ dcos-service-cli linux darwin windows
