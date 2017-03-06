#!/usr/bin/env bash

# exit immediately on failure
set -e

CUR_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
EXE_FILENAME=$(basename $CUR_DIR)
PKG_FILENAME=${EXE_FILENAME}.zip

cd $CUR_DIR

../../tools/build_go_exe.sh sdk/bootstrap/ linux

rm -f $PKG_FILENAME
# build_go_exe.sh produces 'bootstrap-linux': add to zip as 'bootstrap'
mv ${EXE_FILENAME}-linux $EXE_FILENAME
zip -q $PKG_FILENAME $EXE_FILENAME
# preserve original naming so that build_go_exe.sh can skip builds when unneeded:
mv $EXE_FILENAME ${EXE_FILENAME}-linux

echo $(pwd)/${PKG_FILENAME}
