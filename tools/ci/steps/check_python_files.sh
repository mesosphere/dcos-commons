#!/bin/bash

set -e -x

TOOL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/../checks" && pwd )"

export COMPARE_TO=$( ${TOOL_DIR}/get_base_branch.sh )

CHANGESET=$( ${TOOL_DIR}/get_changeset.sh | grep -E "\.py" )

${TOOL_DIR}/run_flake8_checks.sh "${CHANGESET}"
