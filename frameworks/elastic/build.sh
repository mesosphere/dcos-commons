#!/usr/bin/env bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT_DIR=$(dirname $(dirname $FRAMEWORK_DIR))

# grab TEMPLATE_x vars for use in universe template:
source $FRAMEWORK_DIR/versions.sh

# Build SDK artifacts (executor, clis, bootstrap) to be included in our release, but skip SDK tests
# since since that's not in our scope. Projects that aren't colocated in dcos-commons should skip
# this step, and should omit the "REPO_ROOT_DIR" artifacts listed below.
$REPO_ROOT_DIR/build.sh -b

# Build/test our scheduler.zip
${REPO_ROOT_DIR}/gradlew -p ${FRAMEWORK_DIR} check distZip

# Build package with our scheduler.zip and the local SDK artifacts we built:
$REPO_ROOT_DIR/tools/build_package.sh \
    beta-elastic \
    $FRAMEWORK_DIR \
    -a "$FRAMEWORK_DIR/build/distributions/$(basename $FRAMEWORK_DIR)-scheduler.zip" \
    -a "$REPO_ROOT_DIR/sdk/executor/build/distributions/executor.zip" \
    -a "$REPO_ROOT_DIR/sdk/bootstrap/bootstrap.zip" \
    -a "$REPO_ROOT_DIR/sdk/cli/dcos-service-cli-linux" \
    -a "$REPO_ROOT_DIR/sdk/cli/dcos-service-cli-darwin" \
    -a "$REPO_ROOT_DIR/sdk/cli/dcos-service-cli.exe" \
    $@

# Chain to build kibana as well
if [ "$UNIVERSE_URL_PATH" ]; then
    # append kibana stub universe URL to UNIVERSE_URL_PATH file (used in CI):
    KIBANA_URL_PATH=${UNIVERSE_URL_PATH}.kibana
    UNIVERSE_URL_PATH=$KIBANA_URL_PATH $FRAMEWORK_DIR/build-kibana.sh $1
    cat $KIBANA_URL_PATH >> $UNIVERSE_URL_PATH
else
    $FRAMEWORK_DIR/build-kibana.sh $1
fi
