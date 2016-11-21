#!/bin/bash

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

if [ $# -lt 2 ]; then
    echo "Syntax: $0 <framework-name> </path/to/framework> [local|aws]"
    exit 1
fi

FRAMEWORK_NAME=$1
FRAMEWORK_DIR=$2
PUBLISH_STEP=$3

# default paths/names within a framework directory:
CLI_DIR=${CLI_DIR:=${FRAMEWORK_DIR}/cli}
UNIVERSE_DIR=${UNIVERSE_DIR:=${FRAMEWORK_DIR}/universe}
CLI_EXE_NAME=${CLI_EXE_NAME:=dcos-${FRAMEWORK_NAME}}

TOOLS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT_DIR=$(realpath ${TOOLS_DIR}/..)

# GitHub notifier config
_notify_github() {
    GIT_REPOSITORY_ROOT=$REPO_ROOT_DIR ${TOOLS_DIR}/github_update.py $1 build:${FRAMEWORK_NAME} $2
}

_notify_github pending "Build running"

# Service (Java):
${REPO_ROOT_DIR}/gradlew -p ${FRAMEWORK_DIR} clean check distZip
if [ $? -ne 0 ]; then
  _notify_github failure "Gradle build failed"
  exit 1
fi

# CLI (Go):
# /home/user/dcos-commons/frameworks/helloworld/cli => frameworks/helloworld/cli
REPO_CLI_RELATIVE_PATH="$(echo $CLI_DIR | cut -c $((2 + ${#REPO_ROOT_DIR}))-)"
${TOOLS_DIR}/build_cli.sh ${CLI_EXE_NAME} ${CLI_DIR} ${REPO_CLI_RELATIVE_PATH}
if [ $? -ne 0 ]; then
    _notify_github failure "CLI build failed"
    exit 1
fi

_notify_github success "Build succeeded"

case "$3" in
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
        echo "- 'local': Host the build in a local HTTP server for use by a vagrant cluster."
        echo "- 'aws': Upload the build to S3."
        ;;
esac

if [ -n "$PUBLISH_SCRIPT" ]; then
    $PUBLISH_SCRIPT \
        ${FRAMEWORK_NAME} \
        ${UNIVERSE_DIR} \
        ${FRAMEWORK_DIR}/build/distributions/*.zip \
        ${CLI_DIR}/dcos-*/dcos-* \
        ${CLI_DIR}/dcos-*/*.whl
fi
