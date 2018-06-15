#!/bin/bash

TOOL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CHANGESET="$*"

patterns=(
    "^cli"
    "^clivendor"
    "^conftest.py"
    "^govendor"
    "^sdk"
    "^sdk"
    "^test_requirements.txt"
    "^testing"
    "^tools"
    "^tools"
)

if [[ -n $FRAMEWORK ]]; then
    patterns+=("^frameworks/$FRAMEWORK")
fi

IGNORE="\.md\$"

DIFF_FILES=$( echo "${CHANGESET}" | grep -vE "${IGNORE}" )

matches=()
for f in "${patterns[@]}"; do
    matches+=($(echo "${DIFF_FILES}" | grep -E "$f" ))
done


echo "${matches[@]}"
