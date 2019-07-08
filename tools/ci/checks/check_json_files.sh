#!/usr/bin/env bash

# This script is used by the build system to check all Jason files in the
# repository.
#
# By default, the BASE_BRANCH is determined using the get_base_branch script,
# but this can be overridden by setting the BASE_BRANCH environment variable
# before invoking this script

set -x

TOOL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}" )/../checks" && pwd)"

# Determine the target branch for the diff calculation.
BASE_BRANCH="${BASE_BRANCH:-$("${TOOL_DIR}/get_base_branch.sh")}"

# Get the list of changed .json files relative to the base branch.
CHANGESET="$("${TOOL_DIR}/get_applicable_changes.py" --extensions ".json" --from-git "${BASE_BRANCH}")"

if [[ -n ${CHANGESET} ]]; then
  echo "Changeset:"
  echo "${CHANGESET}"

  exec "${TOOL_DIR}/check_json_format.sh" --files ${CHANGESET}

  exit $?
else
  echo "No Json files in changeset."
fi
