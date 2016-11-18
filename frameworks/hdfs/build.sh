#!/usr/bin/env bash

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

REFERENCE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REFERENCE_DIR

ROOT_DIR=$REFERENCE_DIR/../..

# GitHub notifier config
_notify_github() {
    GIT_REPOSITORY_ROOT=$ROOT_DIR $ROOT_DIR/tools/github_update.py $1 build:reference $2
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
  reference \
  universe/ \
  build/distributions/*.zip \
  cli/dcos-hdfs/dcos-hdfs-darwin \
  cli/dcos-hdfs/dcos-hdfs-linux \
  cli/dcos-hdfs/dcos-hdfs.exe \
  cli/python/dist/*.whl \
  ../../sdk/executor/build/distributions/*.zip \
  hdfs-site.xml \
  core-site.xml
