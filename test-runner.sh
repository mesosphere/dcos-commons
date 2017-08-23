#!/usr/bin/env bash

# Build a framework, package, upload it, and then run its integration tests.
# (Or all frameworks depending on arguments.) Expected to be called by test.sh

# Exit immediately on errors
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ "$FRAMEWORK" = "all" ]; then
    # randomize the FRAMEWORK_LIST
    FRAMEWORK_LIST=$(ls $REPO_ROOT_DIR/frameworks | while read -r fw; do printf "%05d %s\n" "$RANDOM" "$fw"; done | sort -n | cut -c7- )
    if [ -n "$STUB_UNIVERSE_URL" ]; then
        echo "Cannot set \$STUB_UNIVERSE_URL when building all frameworks"
        exit 1
    fi
else
    FRAMEWORK_LIST=$FRAMEWORK
fi

echo "Beginning integration tests at "`date`

pytest_args=()

if [ -n "$PYTEST_K" ]; then
    pytest_args+=(-k "$PYTEST_K")
fi

if [ -n "$PYTEST_M" ]; then
    pytest_args+=(-m "$PYTEST_M")
fi

eval "$(ssh-agent -s)"
ssh-add /ssh/key

for framework in $FRAMEWORK_LIST; do
    echo "STARTING: $framework"
    FRAMEWORK_DIR=${REPO_ROOT_DIR}/frameworks/${framework}

    if [ -z "$STUB_UNIVERSE_URL" ]; then
        echo "Starting build for $framework at "`date`
        export UNIVERSE_URL_PATH=${FRAMEWORK_DIR}/$framework-universe-url
        ${FRAMEWORK_DIR}/build.sh aws
        if [ ! -f "$UNIVERSE_URL_PATH" ]; then
            echo "Missing universe URL file: $UNIVERSE_URL_PATH"
            exit 1
        fi
        export STUB_UNIVERSE_URL=$(cat $UNIVERSE_URL_PATH)
        echo "Finished build for $framework at "`date`
    else
        echo "Using provided STUB_UNIVERSE_URL: $STUB_UNIVERSE_URL"
    fi

    echo "Configuring dcoscli for cluster: $CLUSTER_URL"
    /build/tools/dcos_login.py

    echo "Starting test for $framework at "`date`
    PYTHONUNBUFFERED=1 py.test -vv -s "${pytest_args[@]}" ${FRAMEWORK_DIR}/tests
    echo "Finished test for $framework at "`date`
done

echo "Finished integration tests at "`date`
