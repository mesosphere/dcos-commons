#!/bin/bash
set -e

usage() {
    echo "Syntax: $0 [--cli-only] [aws|local]"
}

cli_only=
while :; do
    case $1 in
        --help|-h|-\?)
            usage
            exit
            ;;
        --cli-only)
            cli_only="true"
            ;;
        --)
            shift
            break
            ;;
        -*)
            echo "unknown option $1" >&2
            exit 1
            ;;
        *)
            break
            ;;
    esac

    shift
done

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$(dirname "$(dirname $FRAMEWORK_DIR)")"
export TOOLS_DIR=${ROOT_DIR}/tools
BUILD_DIR=$FRAMEWORK_DIR/build/distributions
PUBLISH_STEP=${1-none}

cli_flag=
if [ x"$cli_only" = xtrue ]; then
    cli_flag=--cli-only
fi

${ROOT_DIR}/tools/build_framework.sh $cli_flag $PUBLISH_STEP hello-world $FRAMEWORK_DIR $BUILD_DIR/executor.zip $BUILD_DIR/hello-world-scheduler.zip $BUILD_DIR/keystore-app.zip
