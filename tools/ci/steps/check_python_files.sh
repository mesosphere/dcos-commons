#!/bin/bash
# This script is used by the build system to check MODIFIED Python files in the repository.

TOOL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/../checks" && pwd )"

# Determine the target branch for the diff calculation
export COMPARE_TO=$( ${TOOL_DIR}/get_base_branch.sh )

# Get the list of Python files in the changeset
CHANGESET=$( ${TOOL_DIR}/get_changeset.sh )

# Further filter the changeset to changes that would trigger a build.
CHANGESET=$( ${TOOL_DIR}/get_applicable_changes.py --extensions ".py" "${CHANGESET}" )

if [[ -n ${CHANGESET} ]]; then
    echo "Changeset:"
    echo "${CHANGESET}"

    echo ""
    echo "Running flake8 on $(echo \"${CHANGESET}\" | wc -w) files:"
    ${TOOL_DIR}/run_flake8_checks.sh "${CHANGESET}"
    rc=$?
    if [ ${rc} -eq 0 ]; then
        echo "Success!"
    fi
    exit ${rc}
fi

echo "No Python files in changeset."
