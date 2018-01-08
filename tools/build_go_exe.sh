#!/usr/bin/env bash

# exit immediately on failure
set -e

syntax() {
    echo "Syntax: $0 <repo-relative/path/to/dir/> <exe-name> <platform> [platform2 platform3 ...]"
    echo "Platforms: 'linux', 'darwin', and/or 'windows'. Must specify at least one."
    echo "Required envvars:"
    echo "- REPO_ROOT_DIR: Path to root of repository"
    echo "- REPO_NAME: Name of repository"
    echo "Optional envvars:"
    echo "- GOPATH_REPO_ORG: Path within GOPATH/src/ under which REPO_NAME resides. GOPATH/src/<GOPATH_REPO_ORG>/<REPO_NAME>/... (default: 'github.com/mesosphere')"
    echo "- SKIP_UPX: If non-empty, disables UPX compression of binaries"
}

if [ $# -lt 2 ]; then
    syntax
    exit 1
fi

RELATIVE_EXE_DIR=$1
shift
EXE_BASE_NAME=$1
shift
PLATFORMS=$@
if [ -z "$RELATIVE_EXE_DIR" -o -z "$EXE_BASE_NAME" -o -z "$PLATFORMS" ]; then
    syntax
    exit 1
fi
echo "Building $EXE_BASE_NAME for [$PLATFORMS] in $RELATIVE_EXE_DIR"

if [ -z "$(which go)" ]; then
    echo "Missing 'go' executable. Please download Go 1.8+ from golang.org, and add 'go' to your PATH."
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
if [ -n "$SKIP_UPX" ]; then
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

# create a fake gopath structure within the repo at ${REPO}/.gopath/
export GOPATH=${REPO_ROOT_DIR}/.gopath

GOPATH_REPO_ORG=${ORG_PATH:=github.com/mesosphere}
# ex: /.gopath/src/github.com/mesosphere
GOPATH_REPO_ORG_DIR=${GOPATH}/src/${GOPATH_REPO_ORG}
# ex: /.gopath/src/github.com/mesosphere/dcos-commons/sdk/cli
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

# Run 'go test'/'go build' from within GOPATH:
cd $GOPATH_EXE_DIR

# optimization: build a native version of the executable and check if the sha1 matches a
# previous native build. if the sha1 matches, then we can skip the rebuild.
NATIVE_FILENAME=".native-${EXE_BASE_NAME}"
NATIVE_SHA1SUM_FILENAME="${NATIVE_FILENAME}.sha1sum"
go build -o $NATIVE_FILENAME
# 'shasum' is available on OSX as well as (most?) Linuxes:
NATIVE_SHA1SUM=$(shasum $NATIVE_FILENAME | awk '{print $1}')

set_platform_filename() {
    # if only one platform is being built, don't append a suffix:
    if [ "$(echo $PLATFORMS | wc -w)" == "1" ]; then
        PLATFORM_FILENAME=${EXE_BASE_NAME}
        return
    fi
    case $1 in
        linux)
            PLATFORM_FILENAME=${EXE_BASE_NAME}-linux
            ;;
        darwin)
            PLATFORM_FILENAME=${EXE_BASE_NAME}-darwin
            ;;
        windows)
            PLATFORM_FILENAME=${EXE_BASE_NAME}.exe
            ;;
        *)
            echo "Unknown platform: $1"
            exit 1
            ;;
    esac
}

ALL_PLATFORM_FILES_EXIST="y"
for PLATFORM in $PLATFORMS; do
    set_platform_filename $PLATFORM
    if [ ! -f $PLATFORM_FILENAME ]; then
        ALL_PLATFORM_FILES_EXIST=""
        break
    fi
done

if [ -f $NATIVE_SHA1SUM_FILENAME -a -n "$ALL_PLATFORM_FILES_EXIST" -a "$NATIVE_SHA1SUM" = "$(cat $NATIVE_SHA1SUM_FILENAME 2>&1)" ]; then
    # build output hasn't changed. skip.
    echo "Up to date, skipping build"
else
    # build output is missing, or native build changed. test and build.

    # Run unit tests, if any '_test.go' files exist and GO_TESTS is not manually disabled:
    if [ x"${GO_TESTS:-true}" == x"true" ]; then
        if [ -n "$(find . -iname '*_test.go')" ]; then
            go test -v
        else
            echo "No unit tests found in $GOPATH_EXE_DIR"
        fi
    fi

    for PLATFORM in $PLATFORMS; do
        set_platform_filename $PLATFORM

        # available GOOS/GOARCH permutations are listed at:
        # https://golang.org/doc/install/source#environment
        CGO_ENABLED=0 GOOS=$PLATFORM GOARCH=386 go build -ldflags="-s -w" -o $PLATFORM_FILENAME

        # use upx if:
        # - upx is installed
        # - golang is recent enough to be compatible with upx
        # - the target OS isn't darwin: compressed darwin builds immediately fail with "Killed: 9"
        if [ -n "$UPX_BINARY" -a "$PLATFORM" != "darwin" ]; then
            $UPX_BINARY -q --best $PLATFORM_FILENAME
        else
            echo "Skipping UPX compression of $PLATFORM_FILENAME"
        fi
    done

    # avoid mistakenly marking old builds as good: update sha1sum AFTER successfully building binaries
    echo $NATIVE_SHA1SUM > $NATIVE_SHA1SUM_FILENAME
fi
