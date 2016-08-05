#!/bin/bash

# This script does a full build/upload of dcos-commons artifacts.
# This script is invoked by Jenkins CI, but may also be run locally on a dev system.

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# In theory, we could use Jenkins' "Multi SCM" script, but good luck with getting auto-build to work with that
# Instead, clone the secondary 'dcos-tests' repo manually.
if [ ! -d dcos-tests ]; then
    git clone --depth 1 git@github.com:mesosphere/dcos-tests.git
fi
echo Running with dcos-tests rev: $(git --git-dir=dcos-tests/.git rev-parse HEAD)

# GitHub notifier config
_notify_github() {
    # IF THIS FAILS FOR YOU, your dcos-tests is out of date!
    # do this: rm -rf dcos-commons/dcos-tests/ then run build.sh again
    $REPO_ROOT_DIR/dcos-tests/build/update-github-status.py $1 $2 $3
}

# Build steps for dcos-commons

_notify_github pending build "Build running"

# Java:

./gradlew clean jar
if [ $? -ne 0 ]; then
  _notify_github failure build "Gradle build failed"
  exit 1
fi

./gradlew check
if [ $? -ne 0 ]; then
  _notify_github failure build "Unit tests failed"
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
    _notify_github failure build "Go CLI build failed"
    exit 1
  fi
else
  echo "NOTICE: Skipping Go CLI build: 'go' executable not found or 'GOPATH' envvar is unset"
fi

_notify_github success build "Build succeeded"
