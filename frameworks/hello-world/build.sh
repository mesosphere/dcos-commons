#!/usr/bin/env bash

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR

ROOT_DIR=$SCRIPT_DIR/../..

# GitHub notifier config
_notify_github() {
    GIT_REPOSITORY_ROOT=$ROOT_DIR $ROOT_DIR/tools/github_update.py $1 build:hello-world $2
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

$ROOT_DIR/tools/ci_upload.py \
  hello-world \
  universe/ \
  build/distributions/*.zip \
  cli/dcos-hello-world/dcos-hello-world-darwin \
  cli/dcos-hello-world/dcos-hello-world-linux \
  cli/dcos-hello-world/dcos-hello-world.exe \
  cli/python/dist/*.whl
