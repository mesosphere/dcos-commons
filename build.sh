#!/bin/bash

# This script does a full build/test of SDK artifacts. This does not upload the artifacts, instead
# see test.sh. This script (and test.sh) are executed by CI upon pull requests to the repository, or
# may be run locally by developers.

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

PULLREQUEST="false"
while getopts 'p' opt; do
    case $opt in
        p)
            PULLREQUEST="true"
            ;;
        \?)
            echo "Unknown option. supported: -p for pull request" >&2
            exit 1
            ;;
    esac
done
shift $((OPTIND-1))

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# GitHub notifier config
_notify_github() {
    $REPO_ROOT_DIR/tools/github_update.py $1 build:sdk $2
}

if [ x$PULLREQUEST = "xtrue" ]; then
  echo "Merging master into pull request branch."
  if ! git merge master; then
    _notify_github failure "Merge from master branch failed"
    exit 1
  fi
fi

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
