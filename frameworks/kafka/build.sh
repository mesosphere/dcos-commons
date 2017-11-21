#!/usr/bin/env bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD_DIR=$FRAMEWORK_DIR/build/distributions

$FRAMEWORK_DIR/setup-helper/build.sh

$FRAMEWORK_DIR/../../tools/build_framework.sh \
    beta-kafka \
    $FRAMEWORK_DIR \
    --artifact "$BUILD_DIR/executor.zip" \
    --artifact "$BUILD_DIR/$(basename $FRAMEWORK_DIR)-scheduler.zip" \
    --artifact "$FRAMEWORK_DIR/setup-helper/setup-helper.zip" \
    $@
