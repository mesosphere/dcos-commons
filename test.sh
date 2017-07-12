#!/usr/bin/env bash

# Build a framework, package, upload it, and then run its integration tests.
# (Or all frameworks depending on arguments.) Depends on a cluster (identified by CLUSTER_URL).

# Exit immediately on errors
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
FRAMEWORK_LIST=$(ls $REPO_ROOT_DIR/frameworks | sort)

if [ $# -eq 0 ]; then
    echo "Usage: $0 all|<framework-name>"
    echo "- Cluster must be created and \$CLUSTER_URL set"
    echo "- AWS credentials must exist in variables:"
    echo "      \$AWS_ACCESS_KEY_ID"
    echo "      \$AWS_SECRET_ACCESS_KEY"
    echo "- Current frameworks:"
    for framework in $FRAMEWORK_LIST; do
        echo "       $framework"
    done
    exit 1
fi

if [ -z "$CLUSTER_URL" ]; then
    echo "Cluster not found. Create and configure one then set \$CLUSTER_URL."
    exit 1
fi

if [ -z "$AWS_ACCESS_KEY_ID" -o -z "$AWS_SECRET_ACCESS_KEY" ]; then
    echo "AWS credentials not found (\$AWS_ACCESS_KEY_ID and \$AWS_SECRET_ACCESS_KEY)."
    exit 1
fi

if [ "$1" = "all" ]; then
    # randomize the FRAMEWORK_LIST
    FRAMEWORK_LIST=$(while read -r fw; do printf "%05d %s\n" "$RANDOM" "$fw"; done <<< "$FRAMEWORK_LIST" | sort -n | cut -c7- )
else
    FRAMEWORK_LIST=$1
fi

echo "Beginning integration tests at "`date`

for framework in $FRAMEWORK_LIST; do
    FRAMEWORK_DIR=${REPO_ROOT_DIR}/frameworks/${framework}

    echo "Starting build for $framework at "`date`
    export UNIVERSE_URL_PATH=${FRAMEWORK_DIR}/$FRAMEWORK-universe-url
    ${FRAMEWORK_DIR}/build.sh aws
    if [ ! -f "$UNIVERSE_URL_PATH" ]; then
        echo "Missing universe URL file: $UNIVERSE_URL_PATH"
        exit 1
    fi
    export STUB_UNIVERSE_URL=$(cat $UNIVERSE_URL_PATH)
    echo "Finished build for $framework at "`date`

    echo "Starting test for $framework at "`date`
    py.test -vv -s -m "sanity" ${FRAMEWORK_DIR}/tests
    echo "Finished test for $framework at "`date`
done

echo "Finished integration tests at "`date`
