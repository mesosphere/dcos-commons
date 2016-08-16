#!/bin/bash

# Exit immediately on failure:
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# Build and upload Java library to Maven repo:
./build.sh
./gradlew publish

# Upload current tools (with '.commit' file containing the current SHA):
echo "$(git rev-parse HEAD)" > tools/.commit \
    && tar czvf tools.tgz tools/.commit tools/*.py \
    && rm tools/.commit \
    && aws s3 cp --acl public-read ./tools.tgz s3://downloads.mesosphere.io/dcos-commons/tools.tgz \
    && rm tools.tgz
