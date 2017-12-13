#!/bin/bash

# This script does a full build/test of SDK artifacts. This does not upload the artifacts, instead
# see test.sh. This script (and test.sh) are executed by CI upon pull requests to the repository, or
# may be run locally by developers.

set -e

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

if [ x$PULLREQUEST = "xtrue" ]; then
    # GitHub notifier: reset any statuses from prior builds for this commit
    $REPO_ROOT_DIR/tools/github_update.py reset

    echo "Creating fake user and merging changes from master."
    # git won't let you update files without knowing a name
    git config user.email pullrequestbot@mesospherebot.com
    git config user.name Infinity-tools-fake-user
    # Update local branch to include github version of master.
    git pull origin $MERGE_FROM --no-commit --ff
fi

# Verify golang-based projects: SDK CLI (exercised via helloworld, our model service)
# and SDK bootstrap, run unit tests
for golang_sub_project in frameworks/helloworld/cli/dcos-hello-world sdk/bootstrap; do
    pushd $golang_sub_project
    go test .
    popd
done

# Build steps for SDK libraries:
./gradlew clean jar check
