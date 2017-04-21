#!/usr/bin/env bash

# exit immediately on failure
set -e

if [ -z "$TOOLS_DIR" ]; then
    TOOLS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
fi

if [ -z "$REPO_ROOT_DIR" ]; then
    REPO_ROOT_DIR="$(dirname $TOOLS_DIR)"
fi

if [ -z "$REPO_NAME" ]; then
    REPO_NAME=dcos-commons # CI dir does not match repo name
fi

export TOOLS_DIR
export REPO_ROOT_DIR
export REPO_NAME
