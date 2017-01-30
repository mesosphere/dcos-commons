#!/bin/bash

# Exit immediately on failure:
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# Build and upload Java library to Maven repo:
./build.sh
./gradlew -p sdk/common publish

# Note: We *don't* run /tools/release.sh here, and instead have CI run it manually.
# This ensures that builds against different tags don't step on each other.
