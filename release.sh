#!/bin/bash

# Exit immediately on failure:
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# Build and upload Java library to Maven repo:
./build.sh
./gradlew publish

# Scripts below expects S3_BUCKET and SDK_VERSION env vars. To be supplied by Jenkins.

# INFINITY-1208: Disable build and publish for now as it requires changes coming in
# the self-hosted branch. 3-27-17 bwood
# ./tools/build_publishable.sh
# ./tools/release_artifacts.sh

# Note: We *don't* run /tools/release.sh here, and instead have CI run it manually.
# This ensures that builds against different tags don't step on each other.
