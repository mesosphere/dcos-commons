#!/usr/bin/env bash

# exit immediately on failure
set -e

syntax() {
    echo "Syntax: $0 <repo-relative/path/to/dir/> <exe-name> <platform:windows|darwin|linux>"
    echo "Required envvars:"
    echo "- REPO_ROOT_DIR: Path to root of repository"
    echo "- REPO_NAME: Name of repository"
    echo "Optional envvars:"
    echo "- GOPATH_REPO_ORG: Path within GOPATH/src/ under which REPO_NAME resides. GOPATH/src/<GOPATH_REPO_ORG>/<REPO_NAME>/... (default: github.com/mesosphere)"
    echo "- CLI_BUILD_SKIP_UPX: If non-empty, disables UPX compression of binaries"
}

if [ $# -lt 2 ]; then
    syntax
    exit 1
fi

RELATIVE_EXE_DIR=$1
EXE_FILENAME=$2
PLATFORM=$3
if [ -z "$RELATIVE_EXE_DIR" -o -z "$EXE_FILENAME" -o -z "$PLATFORM" ]; then
    syntax
    exit 1
fi
echo "Building $EXE_FILENAME for $PLATFORM in $RELATIVE_EXE_DIR"

if [ -z "$GOPATH" -o -z "$(which go)" ]; then
    echo "Missing GOPATH environment variable or 'go' executable. Please configure a Go build environment."
    syntax
    exit 1
fi

if [ -z "$REPO_ROOT_DIR" -o -z "$REPO_NAME" ]; then
    echo "Missing REPO_ROOT_DIR or REPO_NAME environment variables."
    syntax
    exit 1
fi

# Detect Go version to determine if the user has a compatible Go version or not.
GO_VERSION=$(go version | awk '{print $3}')
# Note, UPX only works on binaries produced by Go 1.7+. However, we require Go 1.8+
UPX_BINARY="$(which upx || which upx-ucl || echo '')"
# For dev iteration; upx takes a long time; can set env var
if [ -n "$CLI_BUILD_SKIP_UPX" ]; then
    UPX_BINARY=
fi
case "$GO_VERSION" in
    go1.[8-9]*|go1.1[0-9]*|go[2-9]*) # go1.8+, go2+ (must come before go1.0-go1.7: support e.g. go1.10)
        ;;
    go0.*|go1.[0-7]*) # go0.*, go1.0-go1.7
        echo "Detected Go <=1.7. This is too old, please install Go 1.8+: $(which go) $GO_VERSION"
        exit 1
        ;;
    *) # ???
        echo "Unrecognized go version: $(which go) $GO_VERSION"
        exit 1
        ;;
esac

GOPATH_REPO_ORG=${ORG_PATH:=github.com/mesosphere}
GOPATH_REPO_ORG_DIR="$GOPATH/src/$GOPATH_REPO_ORG"
GOPATH_EXE_DIR="$GOPATH_REPO_ORG_DIR/$REPO_NAME/$RELATIVE_EXE_DIR"

# Add symlink from GOPATH which points into the repository directory, if necessary:
SYMLINK_LOCATION="$GOPATH_REPO_ORG_DIR/$REPO_NAME"
if [ ! -h "$SYMLINK_LOCATION" -o "$(readlink $SYMLINK_LOCATION)" != "$REPO_ROOT_DIR" ] && [ ! -d "$SYMLINK_LOCATION" -o "$SYMLINK_LOCATION" != "$REPO_ROOT_DIR" ]; then
    echo "Creating symlink from GOPATH=$SYMLINK_LOCATION to REPOPATH=$REPO_ROOT_DIR"
    rm -rf "$SYMLINK_LOCATION"
    mkdir -p "$GOPATH_REPO_ORG_DIR"
    cd $GOPATH_REPO_ORG_DIR
    ln -s "$REPO_ROOT_DIR" $REPO_NAME
fi

# Run 'go get'/'go build' from within GOPATH:
cd $GOPATH_EXE_DIR

go get

# run unit tests
go test -v

# optimization: build a native version of the executable and check if the sha1 matches a
# previous native build. if the sha1 matches, then we can skip the rebuild.
NATIVE_FILENAME=".native-${EXE_FILENAME}"
NATIVE_SHA1SUM_FILENAME="${NATIVE_FILENAME}.sha1sum"
go build -o $NATIVE_FILENAME
# 'shasum' is available on OSX as well as (most?) Linuxes:
NATIVE_SHA1SUM=$(shasum $NATIVE_FILENAME | awk '{print $1}')

if [ -f $NATIVE_SHA1SUM_FILENAME -a -f $EXE_FILENAME -a "$NATIVE_SHA1SUM" = "$(cat $NATIVE_SHA1SUM_FILENAME 2>&1)" ]; then
    # build output hasn't changed. skip.
    echo "$EXE_FILENAME: Up to date, skipping build"
else
    # build output is missing, or native build changed. build.
    echo $NATIVE_SHA1SUM > $NATIVE_SHA1SUM_FILENAME

    # available GOOS/GOARCH permutations are listed at:
    # https://golang.org/doc/install/source#environment
    CGO_ENABLED=0 GOOS=$PLATFORM GOARCH=386 go build -ldflags="-s -w" -o $EXE_FILENAME

    # use upx if:
    # - upx is installed
    # - golang is recent enough to be compatible with upx
    # - the target OS isn't darwin: compressed darwin builds immediately fail with "Killed: 9"
    if [ -n "$UPX_BINARY" -a "$PLATFORM" != "darwin" ]; then
        $UPX_BINARY -q --best $EXE_FILENAME
    else
        echo "Skipping UPX compression of $EXE_FILENAME"
    fi
fi
