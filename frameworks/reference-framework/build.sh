#!/usr/bin/env bash

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR

ROOT_DIR=$SCRIPT_DIR/../..

# GitHub notifier config
_notify_github() {
    GIT_REPOSITORY_ROOT=$ROOT_DIR $ROOT_DIR/tools/github_update.py $1 build:reference-framework $2
}

_notify_github pending "Build running"

# Service (Java):
$ROOT_DIR/gradlew clean check distZip
if [ $? -ne 0 ]; then
  _notify_github failure "Gradle build failed"
  exit 1
fi

# CLI (Go):
./cli/build-cli.sh
if [ $? -ne 0 ]; then
    _notify_github failure "CLI build failed"
    exit 1
fi

_notify_github success "Build succeeded"

case "$1" in
    local)
        echo "Launching HTTP artifact server"
        PUBLISH_SCRIPT=$ROOT_DIR/tools/publish_http.py
        ;;
    aws)
        echo "Uploading to S3"
        PUBLISH_SCRIPT=$ROOT_DIR/tools/publish_aws.py
        ;;
    *)
        echo "Skipping publish step."
        echo "Run script as '$0 local' to share build with docker cluster, or '$0 aws' to upload to aws."
        ;;
esac

if [ -n "$PUBLISH_SCRIPT" ]; then
    $PUBLISH_SCRIPT \
        reference-framework \
        universe/ \
        build/distributions/*.zip \
        cli/dcos-reference-framework/dcos-reference-framework* \
        cli/python/dist/*.whl
fi
