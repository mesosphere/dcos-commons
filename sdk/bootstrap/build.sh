#!/usr/bin/env bash

# exit immediately on failure
set -e

BOOTSTRAP_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
REPO_ROOT_DIR="$(dirname $(dirname $BOOTSTRAP_DIR))"

if [ -z "$GOPATH" -o -z "$(which go)" ]; then
  echo "Missing GOPATH environment variable or 'go' executable. Please configure a Go build environment."
  exit 1
fi

REPO_NAME=dcos-commons # CI dir does not match repo name
GOPATH_MESOSPHERE="$GOPATH/src/github.com/mesosphere"
GOPATH_BOOTSTRAP="$GOPATH_MESOSPHERE/$REPO_NAME/sdk/bootstrap"
EXE_FILENAME=bootstrap

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

# Configure GOPATH with dcos-commons symlink (rather than having it pull master):
echo "Creating GOPATH symlink into dcos-commons: $GOPATH"
rm -rf "$GOPATH_MESOSPHERE/$REPO_NAME"
mkdir -p "$GOPATH_MESOSPHERE"
pushd "$GOPATH_MESOSPHERE"
ln -s "$REPO_ROOT_DIR" $REPO_NAME
popd
echo "Created symlink $GOPATH_MESOSPHERE/$REPO_NAME -> $REPO_ROOT_DIR"

# run get/build from within GOPATH:
pushd "$GOPATH_BOOTSTRAP"
go get
echo "Building ${EXE_FILENAME}"
rm -f ${EXE_FILENAME}
CGO_ENABLED=0 GOOS=linux GOARCH=386 go build -ldflags="-s -w"
# use upx if available and if golang's output doesn't have problems with it:
if [ -n "$UPX_BINARY" ]; then
    $UPX_BINARY -q -9 "${EXE_FILENAME}"
fi
PKG_FILENAME=${EXE_FILENAME}.zip
rm -f ${PKG_FILENAME}
zip ${PKG_FILENAME} ${EXE_FILENAME}
echo $(pwd)/${PKG_FILENAME}
popd
