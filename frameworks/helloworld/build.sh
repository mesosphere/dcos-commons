#!/usr/bin/env bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD_DIR=$FRAMEWORK_DIR/build/distributions

export FRAMEWORK_NAME=hello-world # dir (helloworld) is different from package (hello-world)
../../tools/build_framework.sh \
    $FRAMEWORK_DIR \
    --artifact "$BUILD_DIR/executor.zip" \
    --artifact "$BUILD_DIR/${FRAMEWORK_NAME}-scheduler.zip" \
    --artifact "$BUILD_DIR/keystore-app.zip" \
    $@
