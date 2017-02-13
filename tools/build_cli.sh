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
REPO_ROOT_DIR=$(dirname $TOOLS_DIR) # note: need an absolute path for REPO_CLI_RELATIVE_PATH below

if [ -z "$GOPATH" -o -z "$(which go)" ]; then
  echo "Missing GOPATH environment variable or 'go' executable. Please configure a Go build environment."
  exit 1
fi

print_file_and_shasum() {
    echo ""
    # Only show 'file <filename>' if that utility is available: often missing in CI builds.
    if [ -n "$(which file)" ]; then
        file "$1"
    fi
    ls -lh "$1"
    echo ""
}

# ---

# go (static binaries containing the CLI itself)

GO_VERSION=$(go version | awk '{print $3}')
UPX_BINARY="" # only enabled for go1.7+
case "$GO_VERSION" in
    go1.[7-9]*|go1.1[0-9]*|go[2-9]*) # go1.7+, go2+ (must come before go1.0-go1.4: support e.g. go1.10)
        echo "Detected Go 1.7.x+: $(which go) $GO_VERSION"
        UPX_BINARY="$(which upx || which upx-ucl || echo '')" # avoid error code if upx isn't installed
        ;;
    go0.*|go1.[0-4]*) # go0.*, go1.0-go1.4
        echo "Detected Go <=1.4. This is too old, please install Go 1.5+: $(which go) $GO_VERSION"
        exit 1
        ;;
    go1.5*) # go1.5
        echo "Detected Go 1.5.x: $(which go) $GO_VERSION"
        export GO15VENDOREXPERIMENT=1
        ;;
    go1.6*) # go1.6
        echo "Detected Go 1.6.x: $(which go) $GO_VERSION"
        # no experiment, but also no UPX
        ;;
    *) # ???
        echo "Unrecognized go version: $(which go) $GO_VERSION"
        exit 1
        ;;
esac

if [ -n "$UPX_BINARY" ]; then
    echo "Binary CLI compression enabled: $($UPX_BINARY -V | head -n 1)"
else
    echo "Binary CLI compression disabled"
fi

build_cli() {
    EXE_OUTPUT=${CLI_EXE_NAME}$2
    if [ "$1" = "darwin" ]; then
        # do not compress darwin binaries: upx compression results in 'Killed: 9'
        echo "Building $1 CLI: $EXE_OUTPUT (stripped)"
        # available GOOS/GOARCH permutations are listed at:
        # https://golang.org/doc/install/source#environment
        CGO_ENABLED=0 GOOS=$1 GOARCH=386 go build -ldflags="-s -w"
        if [ "${EXE_OUTPUT}" != "${CLI_EXE_NAME}" -a -f "${CLI_EXE_NAME}" ]; then
            mv -vf "${CLI_EXE_NAME}" "${EXE_OUTPUT}"
        fi
        print_file_and_shasum "${EXE_OUTPUT}"
        return
    fi
    echo "Building $1 CLI: $EXE_OUTPUT (stripped/compressed)"
    CGO_ENABLED=0 GOOS=$1 GOARCH=386 go build -ldflags="-s -w"
    if [ "${EXE_OUTPUT}" != "${CLI_EXE_NAME}" -a -f "${CLI_EXE_NAME}" ]; then
        mv -vf "${CLI_EXE_NAME}" "${EXE_OUTPUT}"
    fi
    # use upx if available and if golang's output doesn't have problems with it:
    if [ -n "$UPX_BINARY" ]; then
        $UPX_BINARY -q -9 "${EXE_OUTPUT}"
    fi
    print_file_and_shasum "${EXE_OUTPUT}"
}

# Configure GOPATH with dcos-commons symlink (rather than having it pull master):
echo "Creating GOPATH symlink into dcos-commons: $GOPATH"
REPO_NAME=dcos-commons # CI dir does not match repo name
GOPATH_MESOSPHERE=$GOPATH/src/github.com/mesosphere
rm -rf $GOPATH_MESOSPHERE/$REPO_NAME
mkdir -p $GOPATH_MESOSPHERE
pushd $GOPATH_MESOSPHERE
ln -s $REPO_ROOT_DIR $REPO_NAME
popd
echo "Created symlink $GOPATH_MESOSPHERE/$REPO_NAME -> $REPO_ROOT_DIR"

# run get/build from within GOPATH:
pushd $GOPATH_MESOSPHERE/$REPO_NAME/$REPO_CLI_RELATIVE_PATH/$CLI_EXE_NAME/
go get
build_cli windows .exe # use default .exe suffix
build_cli darwin -darwin # add -darwin suffix
build_cli linux -linux # add -linux suffix
popd

# ---

# python (wraps above binaries for compatibility with DC/OS 1.7 and universe-2.x):
echo "Building Python CLI wrapper (DC/OS 1.7 / universe-2.x compatibility)"
pushd ${TOOLS_DIR}/pythoncli
rm -rf dist/ binaries/
EXE_BUILD_DIR=$CLI_DIR/$CLI_EXE_NAME/ python setup.py bdist_wheel
print_file_and_shasum $CLI_DIR/$CLI_EXE_NAME/*.whl
popd
