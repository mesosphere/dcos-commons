#!/usr/bin/env bash

set -e

usage() {
    # This script is generally called by an upstream 'build.sh'. This describes the commands that are meant to be user facing.
    # Direct callers of build_framework.sh (i.e. framework developers) should see below...
    echo "Syntax: build.sh [-h|--help] [aws|local] [--cli-only]"
}

# SYNTAX FOR AUTHORS OF BUILD.SH:
#   build_framework.sh </abs/path/framework> [aws|local] [--cli-only] [--artifact 'path1' --artifact 'path2' ...]
# Optional envvars:
#   FRAMEWORK_NAME: name of the service to be built (default: directory name of /abs/path/framework)
#   REPO_ROOT_DIR: path to root of source repository (default: parent directory of this file)
#   REPO_NAME: name of the source repository (default: directory name of REPO_ROOT_DIR)
#   BOOTSTRAP_DIR: path to bootstrap tool, or an empty string to disable bootstrap build (default: <REPO_ROOT_DIR>/sdk/bootstrap)
#   CLI_DIR: path to the CLI directory, or an empty string to disable CLI build (default: </absolute/framework/path>/cli/)
#   UNIVERSE_DIR: path to universe packaging (default: </absolute/framework/path>/universe/)


if [ $# -lt 2 ]; then
    usage
    exit 1
fi

# required arg:
FRAMEWORK_DIR=$1
shift

# optional args:
cli_only=
custom_artifacts=
publish_method="no"
while [ $# -gt 0 ]; do
    case $1 in
        --help|-h|-\?)
            usage
            exit
            ;;
        --cli-only)
            cli_only="true"
            ;;
        --artifact)
            # allow append across args:
            custom_artifacts="$custom_artifacts $2"
            shift
            ;;
        -*)
            echo "unknown option: $1" >&2
            usage
            exit 1
            ;;
        aws)
            publish_method="aws"
            ;;
        local)
            publish_method="local"
            ;;
        *)
            echo "unknown argument: $1" >&2
            usage
            exit 1
            ;;
    esac
    shift
done

TOOLS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
export REPO_ROOT_DIR=${REPO_ROOT_DIR:=$(dirname $TOOLS_DIR)} # default to parent of tools dir
export REPO_NAME=$(basename $REPO_ROOT_DIR) # default to name of REPO_ROOT_DIR

# optional customizable names/paths:
FRAMEWORK_NAME=${FRAMEWORK_NAME:=$(basename $FRAMEWORK_DIR)}
BOOTSTRAP_DIR=${BOOTSTRAP_DIR:=${REPO_ROOT_DIR}/sdk/bootstrap}
CLI_DIR=${CLI_DIR:=${FRAMEWORK_DIR}/cli}
UNIVERSE_DIR=${UNIVERSE_DIR:=${FRAMEWORK_DIR}/universe}


# Used below in-order, but here for cli-only
build_cli() {
    # CLI (Go):
    # /home/user/dcos-commons/frameworks/helloworld/cli => frameworks/helloworld/cli
    REPO_CLI_RELATIVE_PATH="$(echo $CLI_DIR | cut -c $((2 + ${#REPO_ROOT_DIR}))-)"
    $TOOLS_DIR/build_go_exe.sh $REPO_CLI_RELATIVE_PATH/ dcos-${FRAMEWORK_NAME}-linux linux
    $TOOLS_DIR/build_go_exe.sh $REPO_CLI_RELATIVE_PATH/ dcos-${FRAMEWORK_NAME}-darwin darwin
    $TOOLS_DIR/build_go_exe.sh $REPO_CLI_RELATIVE_PATH/ dcos-${FRAMEWORK_NAME}.exe windows
}

if [ x"$cli_only" = xtrue ]; then
    build_cli
    exit
fi


echo "Building $FRAMEWORK_NAME in $FRAMEWORK_DIR:"
if [ -n "$BOOTSTRAP_DIR" ]; then
    echo "- Bootstrap: $BOOTSTRAP_DIR (and bootstrap.zip artifacts)"
else
    echo "- Bootstrap: disabled"
fi
if [ -n "$CLI_DIR" ]; then
    echo "- CLI:       $CLI_DIR (and CLI artifacts)"
else
    echo "- CLI:       disabled"
fi
echo "- Universe:  $UNIVERSE_DIR"
echo "- Artifacts:$custom_artifacts"
echo "- Publish:   $publish_method"
echo "---"


# Verify airgap (except for hello world)
if [ $FRAMEWORK_NAME != "hello-world" ];
then
    ${TOOLS_DIR}/airgap_linter.py ${FRAMEWORK_DIR}
fi

# Ensure executor build up to date
${REPO_ROOT_DIR}/gradlew distZip -p ${REPO_ROOT_DIR}/sdk/executor

# Service (Java):
${REPO_ROOT_DIR}/gradlew -p ${FRAMEWORK_DIR} check distZip

BOOTSTRAP_ARTIFACT=""
if [ -n "$BOOTSTRAP_DIR" ]; then
    # Executor Bootstrap (Go):
    ${BOOTSTRAP_DIR}/build.sh
    BOOTSTRAP_ARTIFACT="${BOOTSTRAP_DIR}/bootstrap.zip"
fi

CLI_ARTIFACTS=""
if [ -n "$CLI_DIR" ]; then
    build_cli
    CLI_ARTIFACTS="${CLI_DIR}/dcos-*-linux ${CLI_DIR}/dcos-*-darwin ${CLI_DIR}/dcos-*.exe"
fi

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
    $PUBLISH_SCRIPT \
        ${FRAMEWORK_NAME} \
        ${UNIVERSE_DIR} \
        ${BOOTSTRAP_ARTIFACT} \
        ${CLI_ARTIFACTS} \
        ${custom_artifacts}
fi
