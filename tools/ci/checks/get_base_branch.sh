#!/bin/bash

set -e -x

current_branch=${CURRENT_GIT_BRANCH:-$( git symbolic-ref --short HEAD )}

base_branch="master"

if [[ x"$current_branch" == x*"pull/"* ]]; then
    # This is a PR and we need to determine the branch from the API.
    pr_name="${current_branch/pull/pulls}"

    git_repo=$( git remote get-url origin | sed -e 's/.*://g' )
    git_repo="${git_repo//.git/}"

    output=$( curl --silent "https://api.github.com/repos/${git_repo}/${pr_name}" )
    # Note, curl does not return success/failure based on the HTTP code.
    # Check for a valid return value by retrieving the ID.
    pr_id=$( echo "$output" | jq -r .id )
    if [ x"$pr_id" == x"null" ]; then
        # Check for a message
        message=$( echo "$output" | jq -r .message )
        if [ x"$message" == x"Not Found" ]; then
            echo "The specified PR (${git_repo}/${pr_name}) could not be found"
            exit 1
        else
            echo "The cURL output could not be parsed:"
            echo "$output"
        fi
        exit 1
    fi

    base_branch=$( echo "$output" | jq -r .base.ref )
    current_branch=$( echo "$output" | jq -r .head.ref )
fi

echo "${base_branch}"
