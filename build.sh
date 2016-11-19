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

# Build steps for SDK libraries:

_notify_github pending "Build running"

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

_notify_github success "Build succeeded"
