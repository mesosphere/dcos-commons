#!/bin/bash
set -e

# capture anonymous metrics for reporting
curl https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/build-sh-start.png >/dev/null 2>&1

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$(dirname "$(dirname $FRAMEWORK_DIR)")"
export TOOLS_DIR=${ROOT_DIR}/tools
BUILD_DIR=$FRAMEWORK_DIR/build/distributions
PUBLISH_STEP=${1-none}
${ROOT_DIR}/gradlew distZip -p ${ROOT_DIR}/sdk/executor
${ROOT_DIR}/tools/build_framework.sh $PUBLISH_STEP elastic $FRAMEWORK_DIR $BUILD_DIR/executor.zip $BUILD_DIR/elastic-scheduler.zip

# capture anonymous metrics for reporting
curl https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/build-sh-finish.png >/dev/null 2>&1

# Chain to build kibana as well
if [ "$UNIVERSE_URL_PATH" ]; then
    KIBANA_URL_PATH=${UNIVERSE_URL_PATH}.kibana
    UNIVERSE_URL_PATH=$KIBANA_URL_PATH $FRAMEWORK_DIR/build-kibana.sh $1
    cat $KIBANA_URL_PATH >> $UNIVERSE_URL_PATH
else
    $FRAMEWORK_DIR/build-kibana.sh $1
fi
