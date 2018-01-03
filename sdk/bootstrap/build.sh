#!/usr/bin/env bash
set -e

# Builds the "bootstrap" tool for use with common tasks in container environments.
# Produces 1 artifact: bootstrap.zip (containing a linux build of "bootstrap")

CUR_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
EXE_FILENAME=$(basename $CUR_DIR)
PKG_FILENAME=${EXE_FILENAME}.zip

cd $CUR_DIR

# required env:
export REPO_ROOT_DIR=$(dirname $(dirname $CUR_DIR))
export REPO_NAME=$(basename $REPO_ROOT_DIR)
$REPO_ROOT_DIR/tools/build_go_exe.sh sdk/bootstrap/ $EXE_FILENAME linux

rm -f $PKG_FILENAME
zip -q $PKG_FILENAME $EXE_FILENAME
