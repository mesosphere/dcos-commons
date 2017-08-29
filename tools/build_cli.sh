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

TOOLS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

$TOOLS_DIR/build_go_exe.sh $REPO_CLI_RELATIVE_PATH/$CLI_EXE_NAME windows
$TOOLS_DIR/build_go_exe.sh $REPO_CLI_RELATIVE_PATH/$CLI_EXE_NAME darwin
$TOOLS_DIR/build_go_exe.sh $REPO_CLI_RELATIVE_PATH/$CLI_EXE_NAME linux
