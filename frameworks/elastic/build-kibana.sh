#!/bin/bash
set -e

# capture anonymous metrics for reporting
curl https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/build-sh-start.png >/dev/null 2>&1

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$(dirname "$(dirname ${FRAMEWORK_DIR})")"
export TOOLS_DIR=${ROOT_DIR}/tools
export TEMPLATE_ELASTIC_VERSION="5.4.0"
PUBLISH_STEP=${1-none}
UNIVERSE_DIR=${UNIVERSE_DIR:=${FRAMEWORK_DIR}/universe-kibana}
case "$PUBLISH_STEP" in
    local)
        echo "Launching HTTP artifact server"
        PUBLISH_SCRIPT=${TOOLS_DIR}/publish_http.py
        ;;
    aws)
        echo "Uploading to S3"
        PUBLISH_SCRIPT=${TOOLS_DIR}/publish_aws.py
        ;;
    *)
        echo "---"
        echo "Nothing to build as it's a Marathon app, so skipping publish step."
        echo "Use one of the following additional arguments to get something that runs on a cluster:"
        echo "- 'local': Host the build in a local HTTP server for use by a DC/OS Vagrant cluster."
        echo "- 'aws': Upload the build to S3."
        ;;
esac

if [ -n "$PUBLISH_SCRIPT" ]; then
    $PUBLISH_SCRIPT kibana ${UNIVERSE_DIR}
fi

# capture anonymous metrics for reporting
curl https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/build-sh-finish.png >/dev/null 2>&1
