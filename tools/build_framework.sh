#!/bin/bash

# Prevent jenkins from immediately killing the script when a step fails, allowing us to notify github:
set +e

if [ $# -lt 2 ]; then
    echo "Syntax: $0 <framework-name> </path/to/framework> [local|aws]"
    exit 1
fi

PUBLISH_STEP=$1
shift
FRAMEWORK_NAME=$1
shift
FRAMEWORK_DIR=$1
shift
ARTIFACT_FILES=$@

echo PUBLISH_STEP=$PUBLISH_STEP
echo FRAMEWORK_NAME=$FRAMEWORK_NAME
echo FRAMEWORK_DIR=$FRAMEWORK_DIR
echo ARTIFACT_FILES=$ARTIFACT_FILES

# default paths/names within a framework directory:
CLI_DIR=${CLI_DIR:=${FRAMEWORK_DIR}/cli}
UNIVERSE_DIR=${UNIVERSE_DIR:=${FRAMEWORK_DIR}/universe}
CLI_EXE_NAME=${CLI_EXE_NAME:=dcos-${FRAMEWORK_NAME}}
BUILD_BOOTSTRAP=${BUILD_BOOTSTRAP:=yes}

source $TOOLS_DIR/init_paths.sh

# GitHub notifier config
_notify_github() {
    GIT_REPOSITORY_ROOT=$REPO_ROOT_DIR ${TOOLS_DIR}/github_update.py $1 build:${FRAMEWORK_NAME} $2
}

_notify_github pending "Build running"

# Service (Java):
${REPO_ROOT_DIR}/gradlew -p ${FRAMEWORK_DIR} check distZip
if [ $? -ne 0 ]; then
  _notify_github failure "Gradle build failed"
  exit 1
fi

INCLUDE_BOOTSTRAP=""
if [ "$BUILD_BOOTSTRAP" == "yes" ]; then
    # Executor Bootstrap (Go):
    BOOTSTRAP_DIR=${TOOLS_DIR}/../sdk/bootstrap
    ${BOOTSTRAP_DIR}/build.sh
    if [ $? -ne 0 ]; then
        _notify_github failure "Bootstrap build failed"
        exit 1
    fi
    INCLUDE_BOOTSTRAP="${BOOTSTRAP_DIR}/bootstrap.zip"
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

case "$PUBLISH_STEP" in
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
        echo "- 'aws': Upload the build to S3."
        ;;
esac

if [ -n "$PUBLISH_SCRIPT" ]; then
    $PUBLISH_SCRIPT \
        ${FRAMEWORK_NAME} \
        ${UNIVERSE_DIR} \
        ${INCLUDE_BOOTSTRAP} \
        ${CLI_DIR}/dcos-*/dcos-* \
        ${CLI_DIR}/dcos-*/*.whl \
        ${ARTIFACT_FILES}
fi
