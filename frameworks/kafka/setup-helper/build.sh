#!/usr/bin/env bash
set -e

# Builds the 'setup-helper' tool used by the Kafka service.
# Produces 1 artifact: setup-helper.zip (containing a linux build of "setup-helper")

CUR_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
EXE_FILENAME=$(basename $CUR_DIR)
PKG_FILENAME=${EXE_FILENAME}.zip

cd $CUR_DIR

# required env:
export REPO_ROOT_DIR=$(dirname $(dirname $(dirname $CUR_DIR)))
export REPO_NAME=$(basename $REPO_ROOT_DIR)
$REPO_ROOT_DIR/tools/build_go_exe.sh frameworks/kafka/setup-helper/ $EXE_FILENAME linux

rm -f $PKG_FILENAME
zip -q $PKG_FILENAME $EXE_FILENAME
