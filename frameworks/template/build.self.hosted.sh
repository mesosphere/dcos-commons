#!/bin/bash
set -e

# capture anonymous metrics for reporting
curl https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/build-sh-start.png >/dev/null 2>&1

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD_DIR=$FRAMEWORK_DIR/build/distributions
PUBLISH_STEP=${1-none}
export REPO_NAME=template
export BUILD_BOOTSTRAP=no
export TOOLS_DIR=${FRAMEWORK_DIR}/tools
export CLI_DIR=${FRAMEWORK_DIR}/cli
export ORG_PATH=github.com/$REPO_NAME
${FRAMEWORK_DIR}/tools/build_framework.sh $PUBLISH_STEP $REPO_NAME $FRAMEWORK_DIR $BUILD_DIR/$REPO_NAME-scheduler.zip

# capture anonymous metrics for reporting
curl https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/build-sh-finish.png >/dev/null 2>&1
