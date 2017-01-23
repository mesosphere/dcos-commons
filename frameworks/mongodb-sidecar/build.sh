#!/bin/bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR=$FRAMEWORK_DIR/../..
BUILD_DIR=$ROOT_DIR/build/distributions
PUBLISH_STEP=${1-none}
${ROOT_DIR}/gradlew distZip -p ${ROOT_DIR}/sdk/executor
${ROOT_DIR}/tools/build_framework.sh $PUBLISH_STEP mongodb-sidecar $FRAMEWORK_DIR $BUILD_DIR/executor.zip $BUILD_DIR/scheduler.zip
