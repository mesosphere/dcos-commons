#!/usr/bin/env bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD_DIR=$FRAMEWORK_DIR/build/distributions

# grab TEMPLATE_x vars for use in universe template:
source $FRAMEWORK_DIR/versions.sh

$FRAMEWORK_DIR/../../tools/build_framework.sh \
    beta-elastic \
    $FRAMEWORK_DIR \
    --artifact "$BUILD_DIR/executor.zip" \
    --artifact "$BUILD_DIR/$(basename $FRAMEWORK_DIR)-scheduler.zip" \
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
