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
source $FRAMEWORK_DIR/versions.sh

ROOT_DIR="$(dirname "$(dirname $FRAMEWORK_DIR)")"
export TOOLS_DIR=${ROOT_DIR}/tools
BUILD_DIR=$FRAMEWORK_DIR/build/distributions
PUBLISH_STEP=${1-none}

if [ x"$cli_only" = xtrue ]; then
    ${ROOT_DIR}/tools/build_framework.sh --cli-only $PUBLISH_STEP elastic $FRAMEWORK_DIR $BUILD_DIR/executor.zip $BUILD_DIR/elastic-scheduler.zip
    exit $?
fi

${ROOT_DIR}/tools/build_framework.sh $PUBLISH_STEP elastic $FRAMEWORK_DIR $BUILD_DIR/executor.zip $BUILD_DIR/elastic-scheduler.zip

# Chain to build kibana as well
if [ "$UNIVERSE_URL_PATH" ]; then
    KIBANA_URL_PATH=${UNIVERSE_URL_PATH}.kibana
    UNIVERSE_URL_PATH=$KIBANA_URL_PATH $FRAMEWORK_DIR/build-kibana.sh $1
    cat $KIBANA_URL_PATH >> $UNIVERSE_URL_PATH
else
    $FRAMEWORK_DIR/build-kibana.sh $1
fi
