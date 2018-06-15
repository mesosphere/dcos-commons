#!/bin/bash
#

current_branch=${CURRENT_GIT_BRANCH:-$( git symbolic-ref --short HEAD )}

base_branch="master"
real_branch=''

if [[ x"$current_branch" == x*"pull/"* ]]; then
    # This is a PR and we need to determine the branch from the API.
    pr_name="${current_branch/pull/pulls}"

    git_repo="${$(git remote get-url origin | sed -e 's/.*://g')//.git/}"
    output=$( curl "https://api.github.com/repos/${git_repo}/${pr_name}" )
    base_branch=$(echo "$output" | jq -r .base.ref )
    current_branch=$(echo "$output" | jq -r .head.ref)
fi

echo "${base_branch}"
