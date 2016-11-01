#!/bin/bash

# This script does a full build/upload of dcos-commons artifacts.
# This script is invoked by Jenkins CI, but may also be run locally on a dev system.

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# GitHub notifier config
_notify_github() {
    $REPO_ROOT_DIR/tools/github_update.py $1 build $2
}

# Build steps for dcos-commons

_notify_github pending "Build running"

# Java:

./gradlew clean jar
if [ $? -ne 0 ]; then
  _notify_github failure "Gradle build failed"
  exit 1
fi

./gradlew check
if [ $? -ne 0 ]; then
  _notify_github failure "Unit tests failed"
  exit 1
fi

# Bootstrap GOPATH contents (optional):

if [ -n "$GOPATH" ]; then
  echo "Setting up GOPATH (GOPATH: $GOPATH, go: $(which go) => $(go version))"
  # build must be performed within GOPATH, so set things up for that:
  REPO_NAME=dcos-commons # CI dir does not match repo name
  GOPATH_MESOSPHERE=$GOPATH/src/github.com/mesosphere
  rm -rf $GOPATH_MESOSPHERE/$REPO_NAME
  mkdir -p $GOPATH_MESOSPHERE
  cd $GOPATH_MESOSPHERE
  ln -s $REPO_ROOT_DIR $REPO_NAME
  echo "Created symlink $(pwd)/$REPO_NAME -> $REPO_ROOT_DIR"
else
  echo "NOTICE: Skipping Go CLI setup: 'GOPATH' envvar is unset"
fi

_notify_github success "Build succeeded"
