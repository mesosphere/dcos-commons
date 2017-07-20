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

# capture anonymous metrics for reporting
curl https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/build-sh-start.png >/dev/null 2>&1

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$(dirname "$(dirname $FRAMEWORK_DIR)")"
export TOOLS_DIR=${ROOT_DIR}/tools
BUILD_DIR=$FRAMEWORK_DIR/build/distributions
PUBLISH_STEP=${1-none}

if [ x"$cli_only" = xtrue ]; then
    ${ROOT_DIR}/tools/build_framework.sh --cli-only $PUBLISH_STEP hdfs $FRAMEWORK_DIR $BUILD_DIR/executor.zip $BUILD_DIR/hdfs-scheduler.zip
    exit $?
fi

${ROOT_DIR}/tools/build_framework.sh $PUBLISH_STEP hdfs $FRAMEWORK_DIR $BUILD_DIR/executor.zip $BUILD_DIR/hdfs-scheduler.zip

# capture anonymous metrics for reporting
curl https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/build-sh-finish.png >/dev/null 2>&1
