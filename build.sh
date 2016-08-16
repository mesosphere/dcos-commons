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

# Go (optional, build if tools are present):

if [ -n "$(which go)" -a -n "$GOPATH" ]; then
  echo "Building Go CLI example (GOPATH: $GOPATH, go: $(which go) => $(go version))"
  # build must be performed within GOPATH, so set things up for that:
  REPO_NAME=dcos-commons # CI dir does not match repo name
  GOPATH_MESOSPHERE=$GOPATH/src/github.com/mesosphere
  rm -rf $GOPATH_MESOSPHERE/$REPO_NAME
  mkdir -p $GOPATH_MESOSPHERE
  cd $GOPATH_MESOSPHERE
  ln -s $REPO_ROOT_DIR $REPO_NAME
  echo "Created symlink $(pwd)/$REPO_NAME -> $REPO_ROOT_DIR"
  cd $REPO_NAME/cli/_example && go get && ./build-all.sh
  if [ $? -ne 0 ]; then
    _notify_github failure "Go CLI build failed"
    exit 1
  fi
else
  echo "NOTICE: Skipping Go CLI build: 'go' executable not found or 'GOPATH' envvar is unset"
fi

_notify_github success "Build succeeded"
