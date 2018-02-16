#!/usr/bin/env bash
set -e

# This script builds .whl files for the tools/ and testing/ directories, to be published as part of an SDK release.

if [ $# -ne 1 ]; then
    echo "Syntax: $0 <version>"
    exit 1
fi
# convert snapshot releases from e.g. '1.2.3-SNAPSHOT' to '1.2.3+snapshot' to keep python happy. see also PEP-440.
export VERSION=$(echo $1 | sed 's/-/+/g' | tr '[:upper:]' '[:lower:]')

THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $THIS_DIR

build_dir() {
    rm -rf dist/ binaries/
    OUTPUT_NAME=$1 INPUT_DIR=$2 python3 setup.py -q bdist_wheel
}

build_dir tools ..
build_dir testing ../../testing

cat <<EOF
-----
Init test environment with:

rm -rf venv/
virtualenv -p python3 venv
. venv/bin/activate
pip3 install $(pwd)/testing-$VERSION-*.whl
pip3 install $(pwd)/tools-$VERSION-*.whl

Then run tools using:

tools <tool_exe_filename> [args ...]

Or import testing as a library with:

python3
import sys, os.path, testing
sys.path.append(os.path.dirname(testing.__file__))
import <thing in testing>
EOF
