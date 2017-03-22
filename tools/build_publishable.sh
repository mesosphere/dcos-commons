#!/bin/bash

set -e
source ./tools/init_paths.sh

# Build executor.zip
echo "Building executor.zip"
${REPO_ROOT_DIR}/gradlew distZip -p ${REPO_ROOT_DIR}/sdk/executor

# Build bootstrap
echo "Building bootstrap.zip"
${REPO_ROOT_DIR}/sdk/bootstrap/build.sh
