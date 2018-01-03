#!/usr/bin/env bash
set -e

usage() {
    # This script does a full build/test of the SDK's own artifacts: bootstrap, executor, and default CLI
    # This does not upload the artifacts, instead see test.sh or frameworks/*/build.sh.
    # This script (and test.sh) are executed by CI upon pull requests to the repository, or may be run locally by developers.
    echo "Syntax: build.sh [options]"
    echo "Options:"
    echo "  -p         Set by pull requests (or set PULLREQUEST=true)"
    echo "  -t <name>  Branch to merge before build (or set MERGE_FROM=<name>, default master), only used if -p is specified"
    echo "  -b         Just build without tests (or set GO_TESTS=false and JAVA_TESTS=false)"
}

MERGE_FROM=${MERGE_FROM:=master}
while getopts 'pt:b' opt; do
    case $opt in
        p)
            PULLREQUEST="true"
            ;;
        t)
            # hack for testing
            MERGE_FROM="$OPTARG"
            ;;
        b)
            JAVA_TESTS="false"
            export GO_TESTS="false"
            ;;
        \?)
            usage
            exit 1
            ;;
    esac
done
shift $((OPTIND-1))

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BOOTSTRAP_DIR=${BOOTSTRAP_DIR:=${REPO_ROOT_DIR}/sdk/bootstrap}
CLI_DIR=${CLI_DIR:=${REPO_ROOT_DIR}/sdk/cli}
EXECUTOR_DIR=${EXECUTOR_DIR:=${REPO_ROOT_DIR}/sdk/executor}
DISABLED_VALUE="disable"

cd $REPO_ROOT_DIR

if [ x$PULLREQUEST = "xtrue" ]; then
    # GitHub notifier: reset any statuses from prior builds for this commit
    $REPO_ROOT_DIR/tools/github_update.py reset

    echo "Creating fake user and merging changes from: $MERGE_FROM"
    # git won't let you update files without knowing a name
    git config user.email pullrequestbot@mesospherebot.com
    git config user.name Infinity-tools-fake-user
    # Update local branch to include github version of master.
    git pull origin $MERGE_FROM --no-commit --ff
fi

##
# GO BUILD
##

# Build Go bits: bootstrap and default CLI
BOOTSTRAP_ARTIFACT=""
if [ "$BOOTSTRAP_DIR" != $DISABLED_VALUE ]; then
    # Produces: ${BOOTSTRAP_DIR}/bootstrap.zip
    ${BOOTSTRAP_DIR}/build.sh
fi
if [ "$CLI_DIR" != $DISABLED_VALUE ]; then
    # Produces: ${CLI_DIR}/dcos-service-cli-linux ${CLI_DIR}/dcos-service-cli-darwin ${CLI_DIR}/dcos-service-cli.exe
    ${CLI_DIR}/build.sh
fi

##
# JAVA BUILD
##

# Build Java bits: Executor
if [ "$EXECUTOR_DIR" != $DISABLED_VALUE ]; then
    echo "Building executor in $EXECUTOR_DIR"
    ./gradlew distZip -p $EXECUTOR_DIR
fi

# Run ALL Java unit tests
if [ x"${JAVA_TESTS:-true}" == x"true" ]; then
    # Build steps for SDK libraries:
    ./gradlew clean check
fi
