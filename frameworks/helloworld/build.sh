#!/usr/bin/env bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD_DIR=$FRAMEWORK_DIR/build/distributions

FRAMEWORK_NAME=hello-world # dir name is 'helloworld', while framework name is 'hello-world'

DOCUMENTATION_PATH="https://github.com/mesosphere/dcos-commons/blob/master/frameworks/helloworld/README.md" \
    $FRAMEWORK_DIR/../../tools/build_framework.sh \
        $FRAMEWORK_NAME \
        $FRAMEWORK_DIR \
        --artifact "$BUILD_DIR/executor.zip" \
        --artifact "$BUILD_DIR/${FRAMEWORK_NAME}-scheduler.zip" \
        --artifact "$BUILD_DIR/keystore-app.zip" \
        $@
