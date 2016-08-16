#!/bin/bash

# Exit immediately on failure
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

./build.sh
./gradlew publish

tar czvf tools.tgz tools/*.py \
    && aws s3 cp --acl public-read ./tools.tgz s3://downloads.mesosphere.io/dcos-commons/tools.tgz \
    && rm tools.tgz
