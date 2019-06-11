#!/usr/bin/env bash
# This is a separate build script for Kibana. It creates a stub Universe for the Kibana package and optionally
# publishes it to S3 or a local artifact server.
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source $FRAMEWORK_DIR/versions.sh
ROOT_DIR="$(dirname "$(dirname ${FRAMEWORK_DIR})")"
export TOOLS_DIR=${ROOT_DIR}/tools

PUBLISH_STEP=${1-none}
PACKAGE_VERSION=${2-"stub-universe"}
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
    .dcos)
        echo "Uploading .dcos files to S3"
        PUBLISH_SCRIPT=${TOOLS_DIR}/publish_dcos_file.py
        ;;
    *)
        echo "---"
        echo "Nothing to build as it's a Marathon app, so skipping publish step."
        echo "Use one of the following additional arguments to get something that runs on a cluster:"
        echo "- 'local': Host the build in a local HTTP server."
        echo "- 'aws': Upload the build to S3."
        ;;
esac

if [ -n "{$PUBLISH_SCRIPT}" ]; then
  export TEMPLATE_DOCUMENTATION_PATH="https://docs.mesosphere.com/services/elastic/"

  exec "${PUBLISH_SCRIPT}" \
       kibana \
       "${PACKAGE_VERSION}" \
       "${UNIVERSE_DIR}" \
       "${FRAMEWORK_DIR}/kibana/init.sh" \
       "${FRAMEWORK_DIR}/kibana/nginx.conf.tmpl"
fi
