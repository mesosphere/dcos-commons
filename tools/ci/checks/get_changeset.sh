#!/bin/bash
# Get the set of files changed relative to a specifed git reference.
#
# The COMPARE_TO environment variable is used, and if no value is specified
# changes are compared to HEAD. If HEAD is not valid (e.g. this is a first
# commit, a comparison is made against an empty tree object)

if git rev-parse --verify HEAD >/dev/null 2>&1
then
    against=${COMPARE_TO:-HEAD}
else
    # Initial commit: diff against an empty tree object
    against=4b825dc642cb6eb9a060e54bf8d69288fbee4904
fi

DIFF_FILES=$(git diff ${against} --name-only)

echo "${DIFF_FILES}"
