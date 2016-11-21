#!/bin/bash
set -e

# capture anonymous metrics for reporting
curl --fail https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/build-sh-start.png >/dev/null 2>&1

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
${FRAMEWORK_DIR}/../../tools/build_framework.sh hello-world $FRAMEWORK_DIR $1

# capture anonymous metrics for reporting
curl --fail https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/build-sh-finish.png >/dev/null 2>&1
