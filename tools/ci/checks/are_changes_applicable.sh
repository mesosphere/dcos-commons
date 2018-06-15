#!/bin/bash

TOOL_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if git rev-parse --verify HEAD >/dev/null 2>&1
then
    against=${COMPARE_TO:-HEAD}
else
    # Initial commit: diff against an empty tree object
    against=4b825dc642cb6eb9a060e54bf8d69288fbee4904
fi

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
DIFF_FILES=$( ${TOOL_DIR}/get_changeset.sh | grep -vE "${IGNORE}")

matches=()
for f in "${patterns[@]}"; do
    matches+=($(echo "${DIFF_FILES}" | grep -E "$f" ))
done


if [ ${#matches[@]} -gt 0 ]; then
    echo "Changes applicable"
fi

echo "${matches[*]}"
