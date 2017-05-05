#!/bin/bash

# This script does a full build/test of SDK artifacts. This does not upload the artifacts, instead
# see test.sh. This script (and test.sh) are executed by CI upon pull requests to the repository, or
# may be run locally by developers.

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

_notify_github() {
    $REPO_ROOT_DIR/tools/github_update.py $1 build:sdk $2
}

# Build steps for SDK libraries:

_notify_github pending "SDK build running"

./gradlew clean jar
if [ $? -ne 0 ]; then
  _notify_github failure "SDK build failed"
  exit 1
fi

./gradlew check
if [ $? -ne 0 ]; then
  _notify_github failure "SDK unit tests failed"
  exit 1
fi

_notify_github success "SDK build succeeded"
