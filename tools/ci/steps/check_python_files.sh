#!/bin/bash

TOOL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/../checks" && pwd )"

export COMPARE_TO=$( ${TOOL_DIR}/get_base_branch.sh )

CHANGESET=$( ${TOOL_DIR}/get_changeset.sh | grep -E "\.py$" )

if [[ -n ${CHANGESET} ]]; then
    echo "Changeset:"
    echo "${CHANGESET}"

    echo ""
    echo "Running flake8:"
    ${TOOL_DIR}/run_flake8_checks.sh "${CHANGESET}"
    exit $?
fi

echo "No Python files in changeset."
