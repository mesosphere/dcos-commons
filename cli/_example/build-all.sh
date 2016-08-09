#!/bin/bash

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# The name of the binary produced by Go:
if [ -z "$ORIG_EXE_NAME" ]; then
    ORIG_EXE_NAME="_example"
fi
if [ -z "$EXE_NAME" ]; then
    EXE_NAME="example"
fi

# Override the sha256 command using e.g.:
# SHA_EXE="shasum -256" ./build.sh
# Or, disable the command using:
# SHA_EXE="" ./build.sh
if [ -z "$SHA_EXE" ]; then
    SHA_EXE=sha256sum
fi

print_file_and_shasum() {
    # Only show 'file <filename>' if that utility is available: often missing in CI builds.
    if [ -n "$(which file)" ]; then
        file "$1"
    fi
    ls -l "$1"
    if [ -n "$SHA_EXE" ]; then
        $SHA_EXE "$1"
    fi
    echo ""
}

# may be omitted in 1.6+, left here for compatibility with 1.5:
export GO15VENDOREXPERIMENT=1

# available GOOS/GOARCH permutations are listed at:
# https://golang.org/doc/install/source#environment

# windows:
GOOS=windows GOARCH=386 go build \
    && mv -vf "${ORIG_EXE_NAME}.exe" "${EXE_NAME}.exe"
if [ $? -ne 0 ]; then exit 1; fi
print_file_and_shasum "${EXE_NAME}.exe"

# osx (static build):
SUFFIX="-darwin"
CGO_ENABLED=0 GOOS=darwin GOARCH=386 go build \
    && mv -vf "${ORIG_EXE_NAME}" "${EXE_NAME}${SUFFIX}"
if [ $? -ne 0 ]; then exit 1; fi
# don't ever strip the darwin binary: results in a broken/segfaulty build
print_file_and_shasum "${EXE_NAME}${SUFFIX}"

# linux (static build):
SUFFIX="-linux"
CGO_ENABLED=0 GOOS=linux GOARCH=386 go build \
    && mv -vf "${ORIG_EXE_NAME}" "${EXE_NAME}${SUFFIX}"
if [ $? -ne 0 ]; then exit 1; fi
case "$OSTYPE" in
    linux*) strip "${EXE_NAME}${SUFFIX}"
esac
print_file_and_shasum "${EXE_NAME}${SUFFIX}"
