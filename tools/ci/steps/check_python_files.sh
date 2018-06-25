#!/bin/bash
# This script is used by the build system to check MODIFIED Python files in the repository.

TOOL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/../checks" && pwd )"

# Determine the target branch for the diff calculation
BASE_BRANCH=$( ${TOOL_DIR}/get_base_branch.sh )

# Get the list of changed .py files relative to the base branch
CHANGESET=$( ${TOOL_DIR}/get_applicable_changes.py --extensions ".py" --from-git "${BASE_BRANCH}" )

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
