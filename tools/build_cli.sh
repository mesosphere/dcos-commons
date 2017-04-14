#!/bin/bash

# exit immediately on failure
set -e

if [ $# -lt 2 ]; then
    echo "Syntax: $0 <cli-exe-name> </path/to/framework/cli> <repo-relative/path/to/framework/cli>"
    exit 1
fi

CLI_EXE_NAME=$1
CLI_DIR=$2
REPO_CLI_RELATIVE_PATH=$3 # eg 'frameworks/helloworld/cli/'

source $TOOLS_DIR/init_paths.sh

# ---

# go
$TOOLS_DIR/build_go_exe.sh $REPO_CLI_RELATIVE_PATH/$CLI_EXE_NAME windows
$TOOLS_DIR/build_go_exe.sh $REPO_CLI_RELATIVE_PATH/$CLI_EXE_NAME darwin
$TOOLS_DIR/build_go_exe.sh $REPO_CLI_RELATIVE_PATH/$CLI_EXE_NAME linux

# ---

# python (wraps above binaries for compatibility with DC/OS 1.7 and universe-2.x):
echo "Building Python CLI wrapper (DC/OS 1.7 / universe-2.x compatibility)"
cd $TOOLS_DIR/pythoncli
rm -rf dist/ binaries/
EXE_BUILD_DIR=$CLI_DIR/$CLI_EXE_NAME/ python setup.py -q bdist_wheel
