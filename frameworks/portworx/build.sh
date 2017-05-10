#!/usr/bin/env bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT_DIR=$(dirname $(dirname $FRAMEWORK_DIR))

# Build SDK artifacts (executor, bootstrap) to be included in our release, but skip SDK tests
# since since that's not in our scope. Projects that aren't colocated in dcos-commons should skip
# this step, and should omit the "REPO_ROOT_DIR" artifacts listed below.
# Portworx skips the default CLI build because it builds it's own CLI below.
CLI_DIR="disable" $REPO_ROOT_DIR/build.sh -b

# Build/test scheduler.zip/CLIs
${REPO_ROOT_DIR}/gradlew -p ${FRAMEWORK_DIR} check distZip
$FRAMEWORK_DIR/cli/build.sh

# Build package with our scheduler.zip/CLIs and the local SDK artifacts we built:
$REPO_ROOT_DIR/tools/build_package.sh \
    portworx \
    $FRAMEWORK_DIR \
    -a "$FRAMEWORK_DIR/build/distributions/$(basename $FRAMEWORK_DIR)-scheduler.zip" \
    -a "$FRAMEWORK_DIR/cli/dcos-service-cli-linux" \
    -a "$FRAMEWORK_DIR/cli/dcos-service-cli-darwin" \
    -a "$FRAMEWORK_DIR/cli/dcos-service-cli.exe" \
    -a "$REPO_ROOT_DIR/sdk/bootstrap/bootstrap.zip" \
    -a "$REPO_ROOT_DIR/sdk/executor/build/distributions/executor.zip" \
    $@
