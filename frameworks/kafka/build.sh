#!/bin/bash
set -e

FRAMEWORK_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
${FRAMEWORK_DIR}/../../tools/build_framework.sh kafka $FRAMEWORK_DIR $1
