#!/usr/bin/env bash

set -e

user_usage() {
    # This script is generally called by an upstream 'build.sh' which would be invoked directly by users.
    # This function returns the syntax expected to be used by that upstream 'build.sh'
    echo "Syntax: build.sh [-h|--help] [aws|local]"
}

dev_usage() {
    # Called when a syntax error appears to be an error on the part of the developer.
    echo "Developer syntax: build_package.sh <framework-name> </abs/path/to/framework> [-a 'path1' -a 'path2' ...] [aws|local]"
}

# Optional envvars:
#   REPO_ROOT_DIR: path to root of source repository (default: parent directory of this file)
#   REPO_NAME: name of the source repository (default: directory name of REPO_ROOT_DIR)
#   UNIVERSE_DIR: path to universe packaging (default: </absolute/framework/path>/universe/)

if [ $# -lt 3 ]; then
    dev_usage
    exit 1
fi

# required args:
FRAMEWORK_NAME=$1
shift
FRAMEWORK_DIR=$1
shift

echo "Building $FRAMEWORK_NAME in $FRAMEWORK_DIR:"

# optional args, currently just used for providing paths to service artifacts:
custom_artifacts=
while getopts 'a:' opt; do
    case $opt in
        a)
            custom_artifacts="$custom_artifacts $OPTARG"
            ;;
        \?)
            dev_usage
            exit 1
            ;;
    esac
done
shift $((OPTIND-1))

# optional publish method should come after any args:
publish_method="no"
case $1 in
    aws)
        publish_method="aws"
        shift
        ;;
    local)
        publish_method="local"
        shift
        ;;
    "")
        # no publish method specified
        ;;
    *)
        # unknown verb
        user_usage
        exit 1
        ;;
esac

TOOLS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
export REPO_ROOT_DIR=${REPO_ROOT_DIR:=$(dirname $TOOLS_DIR)} # default to parent of this script's dir
export REPO_NAME=${REPO_NAME:=$(basename $REPO_ROOT_DIR)} # default to name of REPO_ROOT_DIR

UNIVERSE_DIR=${UNIVERSE_DIR:=${FRAMEWORK_DIR}/universe} # default to 'universe' directory in framework dir
echo "- Universe:  $UNIVERSE_DIR"
echo "- Artifacts:$custom_artifacts"
echo "- Publish:   $publish_method"
echo "---"

# Verify airgap (except for hello world)
if [ $FRAMEWORK_NAME != "hello-world" ]; then
    ${TOOLS_DIR}/airgap_linter.py ${FRAMEWORK_DIR}
fi

# Upload using requested method
case "$publish_method" in
    local)
        echo "Launching HTTP artifact server"
        PUBLISH_SCRIPT=${TOOLS_DIR}/publish_http.py
        ;;
    aws)
        echo "Uploading to S3"
        PUBLISH_SCRIPT=${TOOLS_DIR}/publish_aws.py
        ;;
    *)
        echo "---"
        echo "Build complete, skipping publish step."
        echo "Use one of the following additional arguments to get something that runs on a cluster:"
        echo "- 'local': Host the build in a local HTTP server for use by a DC/OS Vagrant cluster."
        echo "- 'aws':   Upload the build to S3."
        ;;
esac

if [ -n "$PUBLISH_SCRIPT" ]; then
    # Both scripts use the same argument format:
    $PUBLISH_SCRIPT ${FRAMEWORK_NAME} ${UNIVERSE_DIR} ${custom_artifacts}
fi
