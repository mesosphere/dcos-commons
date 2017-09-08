#!/usr/bin/env bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BUILD_DIR=$FRAMEWORK_DIR/build/distributions

TEMPLATE_DOCUMENTATION_PATH="http://YOURNAMEHERE.COM/DOCS" \
TEMPLATE_ISSUES_PATH="http://YOURNAMEHERE.COM/SUPPORT" \
    $FRAMEWORK_DIR/../../tools/build_framework.sh \
        template \
        $FRAMEWORK_DIR \
        --artifact "$BUILD_DIR/executor.zip" \
        --artifact "$BUILD_DIR/$(basename $FRAMEWORK_DIR)-scheduler.zip" \
        $@
