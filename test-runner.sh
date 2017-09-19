#!/usr/bin/env bash

# Build a framework, package, upload it, and then run its integration tests.
# (Or all frameworks depending on arguments.) Expected to be called by test.sh

# Exit immediately on errors
set -e -x

# Remove the DC/OS cluster
cleanup() {
    dcos-launch delete
}

# Export the required environment variables:
export DCOS_ENTERPRISE
export PYTHONUNBUFFERED=1
export SECURITY


REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ "$FRAMEWORK" = "all" ]; then
    if [ -n "$STUB_UNIVERSE_URL" ]; then
        echo "Cannot set \$STUB_UNIVERSE_URL when building all frameworks"
        exit 1
    fi
    # randomize the FRAMEWORK_LIST
    FRAMEWORK_LIST=$(ls $REPO_ROOT_DIR/frameworks | while read -r fw; do printf "%05d %s\n" "$RANDOM" "$fw"; done | sort -n | cut -c7- )
else
    FRAMEWORK_LIST=$FRAMEWORK
fi

echo "Beginning integration tests at "`date`

# Strip the quotes from the -k and -m options to pytest
PYTEST_K_FROM_PYTEST_ARGS=`echo "$PYTEST_ARGS" \
    | sed -e "s#.*-k [\'\"]\([^\'\"]*\)['\"].*#\1#"`
if [ "$PYTEST_K_FROM_PYTEST_ARGS" == "$PYTEST_ARGS" ]; then
    PYTEST_K_FROM_PYTEST_ARGS=
else
    if [ -n "$PYTEST_K" ]; then
        PYTEST_K="$PYTEST_K "
    fi
    PYTEST_K="${PYTEST_K}${PYTEST_K_FROM_PYTEST_ARGS}"
    PYTEST_ARGS=`echo "$PYTEST_ARGS" \
        | sed -e "s#-k [\'\"]\([^\'\"]*\)['\"]##"`
fi

PYTEST_M_FROM_PYTEST_ARGS=`echo "$PYTEST_ARGS" \
    | sed -e "s#.*-m [\'\"]\([^\'\"]*\)['\"].*#\1#"`
if [ "$PYTEST_M_FROM_PYTEST_ARGS" == "$PYTEST_ARGS" ]; then
    PYTEST_M_FROM_PYTEST_ARGS=
else
    if [ -n "$PYTEST_M" ]; then
        PYTEST_M="$PYTEST_M "
    fi
    PYTEST_M="${PYTEST_M}${PYTEST_M_FROM_PYTEST_ARGS}"
    PYTEST_ARGS=`echo "$PYTEST_ARGS" \
        | sed -e "s#-m [\'\"]\([^\'\"]*\)['\"]##"`
fi


pytest_args=()

# PYTEST_K and PYTEST_M are treated as single strings, and should thus be added
# to the pytest_args array in quotes.
if [ -n "$PYTEST_K" ]; then
    pytest_args+=(-k "$PYTEST_K")
fi

if [ -n "$PYTEST_M" ]; then
    pytest_args+=(-m "$PYTEST_M")
fi

# Each of the space-separated parts of PYTEST_ARGS are treated separately.
if [ -n "$PYTEST_ARGS" ]; then
    pytest_args+=($PYTEST_ARGS)
fi

if [ -f /ssh/key ]; then
    eval "$(ssh-agent -s)"
    ssh-add /ssh/key
fi

for framework in $FRAMEWORK_LIST; do
    echo "STARTING: $framework"
    FRAMEWORK_DIR=$REPO_ROOT_DIR/frameworks/${framework}

    if [ ! -d ${FRAMEWORK_DIR} -a "${FRAMEWORK}" != "all" ]; then
        echo "FRAMEWORK_DIR=${FRAMEWORK_DIR} does not exist."
        echo "Assuming single framework in ${REPO_ROOT}."
        FRAMEWORK_DIR=${REPO_ROOT_DIR}
    fi

    if [ -z "$STUB_UNIVERSE_URL" ]; then
        echo "Starting build for $framework at "`date`
        export UNIVERSE_URL_PATH=${FRAMEWORK_DIR}/${framework}-universe-url
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

    if [ -z "$CLUSTER_URL" ]; then

        echo "No DC/OS cluster specified. Attempting to create one now"
        dcos-launch create -c /build/config.yaml

        # enable the trap to ensure cleanup
        trap cleanup ERR

        dcos-launch wait

        # configure the dcos-cli/shakedown backend
        export CLUSTER_URL=https://`dcos-launch describe | jq -r .masters[0].public_ip`
        CLUSTER_WAS_CREATED=True
    fi

    echo "Configuring dcoscli for cluster: $CLUSTER_URL"
    echo "\tDCOS_ENTERPRISE=$DCOS_ENTERPRISE"
    /build/tools/dcos_login.py

    if [ -f cluster_info.json ]; then
        if [ `cat cluster_info.json | jq .key_helper` == 'true' ]; then
            cat cluster_info.json | jq -r .ssh_private_key > /root/.ssh/id_rsa
            chmod 600 /root/.ssh/id_rsa
        fi
    fi

    echo "Starting test for $framework at "`date`
    py.test -vv -s "${pytest_args[@]}" ${FRAMEWORK_DIR}/tests
    exit_code=$?
    echo "Finished test for $framework at "`date`

    set +e
    if [ -n "$CLUSTER_WAS_CREATED" ]; then
        echo "Deleting cluster $CLUSTER_URL"
        dcos-launch delete
        unset CLUSTER_URL
    fi
done

echo "Finished integration tests at "`date`

exit $exit_code
