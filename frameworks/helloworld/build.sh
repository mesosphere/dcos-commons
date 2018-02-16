#!/usr/bin/env bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT_DIR=$(dirname $(dirname $FRAMEWORK_DIR))

# Build SDK artifacts (executor, clis, bootstrap) to be included in our release, but skip SDK tests
# since since that's not in our scope. Projects that aren't colocated in dcos-commons should skip
# this step, and should omit the "REPO_ROOT_DIR" artifacts listed below.
$REPO_ROOT_DIR/build.sh -b

# Build/test our scheduler.zip and keystore-app.zip
${REPO_ROOT_DIR}/gradlew -p ${FRAMEWORK_DIR} check distZip

# Build package with our scheduler.zip/keystore-app.zip and the local SDK artifacts we built:
TEMPLATE_DOCUMENTATION_PATH="https://github.com/mesosphere/dcos-commons/blob/master/frameworks/helloworld/README.md" \
$REPO_ROOT_DIR/tools/build_package.sh \
    hello-world \
    $FRAMEWORK_DIR \
    -a "$FRAMEWORK_DIR/build/distributions/keystore-app.zip" \
    -a "$FRAMEWORK_DIR/build/distributions/hello-world-scheduler.zip" \
    -a "$REPO_ROOT_DIR/sdk/executor/build/distributions/executor.zip" \
    -a "$REPO_ROOT_DIR/sdk/bootstrap/bootstrap.zip" \
    -a "$REPO_ROOT_DIR/sdk/cli/dcos-service-cli-linux" \
    -a "$REPO_ROOT_DIR/sdk/cli/dcos-service-cli-darwin" \
    -a "$REPO_ROOT_DIR/sdk/cli/dcos-service-cli.exe" \
    $@
