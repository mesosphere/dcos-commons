#!/usr/bin/env bash

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# Grab dcos-commons build/release tools:
rm -rf dcos-commons-tools/ && curl https://infinity-artifacts.s3.amazonaws.com/dcos-commons-tools.tgz | tar xz

_notify_github() {
  $REPO_ROOT_DIR/dcos-commons-tools/github_update.py $1 build $2
}

# Build steps for HDFS

_notify_github pending "Build running"

# CLI (Go):
./cli/build-cli.sh
if [ $? -ne 0 ]; then
    _notify_github failure "CLI build failed"
    exit 1
fi

_notify_github success "Build succeeded"

./dcos-commons-tools/ci_upload.py \
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
