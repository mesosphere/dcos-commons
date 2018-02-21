#!/bin/bash
# Exit immediately on errors
set -e -x

# Export the required environment variables:
export DCOS_ENTERPRISE
export PYTHONUNBUFFERED=1
export SECURITY

BUILD_TOOL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT_DIR="${REPO_ROOT:-$1}"

SINGLE_FRAMEWORK="True"
# Determine the list of frameworks if it is not specified
if [ -z "${FRAMEWORK}" -o x"${AUTO_DETECT_FRAMEWORKS}" == x"True" -o x"$FRAMEWORK" == x"all" ]; then
    if [ -d "$REPO_ROOT_DIR/frameworks" ]; then
        FRAMEWORK_LIST=$(ls $REPO_ROOT_DIR/frameworks)
        SINGLE_FRAMEWORK="False"
    else
        FRAMEWORK_LIST=$(basename ${REPO_ROOT_DIR})
    fi
else
    FRAMEWORK_LIST=$FRAMEWORK
fi

# First we need to build the framework(s)
echo "Using FRAMEWORK_LIST:\n${FRAMEWORK_LIST}"

if [ -n "$STUB_UNIVERSE_URL" ]; then
    if [ x"$SINGLE_FRAMEWORK" == x"False" ]; then
        echo "\$STUB_UNIVERSE_URL can only be set when building single frameworks"
        exit 1
    fi
    echo "Using provided STUB_UNIVERSE_URL: $STUB_UNIVERSE_URL"
else
    for framework in $FRAMEWORK_LIST; do
        FRAMEWORK_DIR=$REPO_ROOT_DIR/frameworks/${framework}
        if [ ! -d ${FRAMEWORK_DIR} ]; then
            echo "FRAMEWORK_DIR=${FRAMEWORK_DIR} does not exist."
            echo "Assuming single framework in ${REPO_ROOT}."
            FRAMEWORK_DIR=${REPO_ROOT_DIR}
        fi

        echo "Starting build for $framework at "`date`
        export UNIVERSE_URL_PATH=${FRAMEWORK_DIR}/${framework}-universe-url
        ${FRAMEWORK_DIR}/build.sh aws
        if [ ! -f "$UNIVERSE_URL_PATH" ]; then
            echo "Missing universe URL file: $UNIVERSE_URL_PATH"
            exit 1
        fi
        if [ -z ${STUB_UNIVERSE_LIST} ]; then
            STUB_UNIVERSE_LIST=$(cat ${UNIVERSE_URL_PATH})
        else
            STUB_UNIVERSE_LIST="${STUB_UNIVERSE_LIST},$(cat ${UNIVERSE_URL_PATH})"
        fi
        echo "Finished build for $framework at "`date`
    done
    export STUB_UNIVERSE_URL=${STUB_UNIVERSE_LIST}
    echo "Using STUB_UNIVERSE_URL: $STUB_UNIVERSE_URL"
fi


# Now create a cluster if it doesn't exist.
if [ -z "$CLUSTER_URL" ]; then
    echo "No DC/OS cluster specified. Attempting to create one now"

    ${BUILD_TOOL_DIR}/launch_cluster.sh ${REPO_ROOT_DIR}/config.yaml ${REPO_ROOT_DIR}/cluster_info.json

    if [ -f ${REPO_ROOT_DIR}/cluster_info.json ]; then
        export CLUSTER_URL=https://$(dcos-launch describe --info-path=${REPO_ROOT_DIR}/cluster_info.json | jq -r .masters[0].public_ip)
        if [ -z $CLUSTER_URL ]; then
            echo "Could not determine CLUSTER_URL"
            exit 1
        fi
        CLUSTER_WAS_CREATED="True"
    else
        echo "Error creating cluster"
        exit 1
    fi
fi

echo "Configuring dcoscli for cluster: $CLUSTER_URL"
echo "\tDCOS_ENTERPRISE=$DCOS_ENTERPRISE"
${REPO_ROOT_DIR}/tools/dcos_login.py

# Ensure that the ssh-agent is running:
eval "$(ssh-agent -s)"
if [ -f /ssh/key ]; then
    ssh-add /ssh/key
fi

if [ -f ${REPO_ROOT_DIR}/cluster_info.json ]; then
    if [ `cat ${REPO_ROOT_DIR}/cluster_info.json | jq .key_helper` == 'true' ]; then
        cat ${REPO_ROOT_DIR}/cluster_info.json | jq -r .ssh_private_key > /root/.ssh/id_rsa
        chmod 600 /root/.ssh/id_rsa
        ssh-add /root/.ssh/id_rsa
    fi
fi


# Determine the pytest args
pytest_args=()

# PYTEST_K and PYTEST_M are treated as single strings, and should thus be added
# to the pytest_args array in quotes.

PYTEST_K=`echo "$PYTEST_ARGS" \
    | sed -e "s#.*-k [\'\"]\([^\'\"]*\)['\"].*#\1#"`
if [ "$PYTEST_K" != "$PYTEST_ARGS" ]; then
    if [ -n "$PYTEST_K" ]; then
        pytest_args+=(-k "$PYTEST_K")
    fi
    PYTEST_ARGS=`echo "$PYTEST_ARGS" \
        | sed -e "s#-k [\'\"]\([^\'\"]*\)['\"]##"`
fi

PYTEST_M=`echo "$PYTEST_ARGS" \
    | sed -e "s#.*-m [\'\"]\([^\'\"]*\)['\"].*#\1#"`
if [ "$PYTEST_M" != "$PYTEST_ARGS" ]; then
    if [ -n "$PYTEST_M" ]; then
        pytest_args+=(-m "$PYTEST_M")
    fi
    PYTEST_ARGS=`echo "$PYTEST_ARGS" \
        | sed -e "s#-m [\'\"]\([^\'\"]*\)['\"]##"`
fi

# Each of the space-separated parts of PYTEST_ARGS are treated separately.
if [ -n "$PYTEST_ARGS" ]; then
    pytest_args+=($PYTEST_ARGS)
fi

# Now run the tests:

# First in the root.
if [ -d ${REPO_ROOT_DIR}/tests ]; then
    FRAMEWORK_TESTS_DIR=${REPO_ROOT_DIR}/tests
    echo "Starting test for $FRAMEWORK_TESTS_DIR at "`date`
    py.test -vv -s "${pytest_args[@]}" ${FRAMEWORK_TESTS_DIR}
    exit_code=$?
    echo "Finished test for $FRAMEWORK_TESTS_DIR at "`date`
fi

# Now each of the selected frameworks:
for framework in $FRAMEWORK_LIST; do
    echo "Checking framework ${framework}"
    FRAMEWORK_TESTS_DIR=$REPO_ROOT_DIR/frameworks/${framework}/tests
    if [ ! -d ${FRAMEWORK_TESTS_DIR} ]; then
        echo "No tests found for ${framework} at ${FRAMEWORK_TESTS_DIR}"
    else
        echo "Starting test for $FRAMEWORK_TESTS_DIR at "`date`
        py.test -vv -s "${pytest_args[@]}" ${FRAMEWORK_TESTS_DIR}
        exit_code=$?
        echo "Finished test for $FRAMEWORK_TESTS_DIR at "`date`
    fi
done

echo "Finished integration tests at "`date`

if [ -n "$CLUSTER_WAS_CREATED" ]; then
    echo "The DC/OS cluster $CLUSTER_URL was created. Please run"
    echo "\t\$ dcos-launch delete --info-path=${CLUSTER_INFO_FILE}"
    echo "to remove the cluster."
fi

exit $exit_code
