#!/usr/bin/env bash
set -e

CUR_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $CUR_DIR

EXE_NAME="dcos-service-cli"

# required env:
export REPO_ROOT_DIR=$(dirname $(dirname $CUR_DIR))
export REPO_NAME=$(basename $REPO_ROOT_DIR)
$REPO_ROOT_DIR/tools/build_go_exe.sh sdk/cli/ ${EXE_NAME}-linux linux
$REPO_ROOT_DIR/tools/build_go_exe.sh sdk/cli/ ${EXE_NAME}-darwin darwin
$REPO_ROOT_DIR/tools/build_go_exe.sh sdk/cli/ ${EXE_NAME}.exe windows

echo $(pwd)/${EXE_NAME}-linux $(pwd)/${EXE_NAME}-darwin $(pwd)/${EXE_NAME}.exe
