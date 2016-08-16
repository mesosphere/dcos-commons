#!/bin/bash

# Exit immediately on failure:
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# Build and upload Java library to Maven repo:
./build.sh
./gradlew publish

# Build and upload tools to S3:
./tools/release.sh
