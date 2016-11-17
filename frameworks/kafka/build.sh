#!/usr/bin/env bash

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR

ROOT_DIR=$SCRIPT_DIR/../..

# GitHub notifier config
_notify_github() {
    GIT_REPOSITORY_ROOT=$ROOT_DIR $ROOT_DIR/tools/github_update.py $1 build:kafka $2
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

$ROOT_DIR/tools/publish_aws.py \
  kafka \
  universe/ \
  build/distributions/*.zip \
  cli/dcos-kafka/dcos-kafka-darwin \
  cli/dcos-kafka/dcos-kafka-linux \
  cli/dcos-kafka/dcos-kafka.exe \
  cli/python/dist/*.whl
