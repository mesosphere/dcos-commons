#!/bin/bash

# This script does a full build/test of SDK artifacts. This does not upload the artifacts, instead
# see test.sh. This script (and test.sh) are executed by CI upon pull requests to the repository, or
# may be run locally by developers.

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

PULLREQUEST="false"
MERGE_FROM="master"
while getopts 'pt:' opt; do
    case $opt in
        p)
            PULLREQUEST="true"
            ;;
        t)
            # hack for testing
            MERGE_FROM="$OPTARG"
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

# GitHub notifier: reset any statuses from prior builds for this commit
$REPO_ROOT_DIR/tools/github_update.py reset

_notify_github() {
    $REPO_ROOT_DIR/tools/github_update.py $1 build:sdk $2
}

merge_master() {
    echo "attempting to merge changes from master"
    # git won't let you update files without knowing a name
    echo "Creating fake user."
    command="git config user.email pullrequestbot@mesospherebot.com"
    echo $command
    if ! $command; then
        return 1 # fail
    fi
    command="git config user.name Infinity-tools-fake-user"
    echo $command
    if ! $command; then
        return 1 # fail
    fi
    # Update local branch to include github version of master.
    command="git pull origin $MERGE_FROM --no-commit --ff"
    echo $command
    if ! $command; then
        return 1 # fail
    fi
    return 0 # ok
}

if [ x$PULLREQUEST = "xtrue" ]; then
  echo "Merging master into pull request branch."
  if ! merge_master; then
    _notify_github failure "Merge from master branch failed"
    exit 1
  else
    _notify_github pending "Merge from master branch done"
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
