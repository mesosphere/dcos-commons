#!/bin/bash
# This script is used by the build system to check MODIFIED Python files in the repository.
#
# By default, the BASE_BRANCH is determined using the get_base_branch script, but this
# can be overridden by setting the BASE_BRANCH environment variable before invoking this
# script

TOOL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )/../checks" && pwd )"

# Determine the target branch for the diff calculation
BASE_BRANCH=${BASE_BRANCH:-$( ${TOOL_DIR}/get_base_branch.sh )}

# Get the list of changed .py files relative to the base branch
CHANGESET="$( ${TOOL_DIR}/get_applicable_changes.py --extensions ".py" --from-git "${BASE_BRANCH}" )"

FAILURES=
if [[ -n ${CHANGESET} ]]; then
    echo "Changeset:"
    echo "${CHANGESET}"

    TOOLS=("flake8" "pylint")
    SEP=
    for tool in ${TOOLS[@]}; do
        echo
        echo "Running ${tool} on $( echo \"${CHANGESET}\" | wc -w ) files:"
        tool_script="${TOOL_DIR}/run_${tool}_checks.sh"

        ${tool_script} ${CHANGESET}
        rc=$?
        if [ ${rc} -eq 0 ]; then
            echo "Success!"
        else
            echo
            echo "ERROR: ${tool} failed with rc=${rc}"
            FAILURES="${FAILURES}${SEP}tool=${tool}:rc=${rc}"
            SEP=", "
        fi
    done

    if [ -z $FAILURES ]; then
        exit 0
    else
        echo "The following tool(s) failed: ${FAILURES}"
        exit 1
    fi
else
    echo "No Python files in changeset."
fi
