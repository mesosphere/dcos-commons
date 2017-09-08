#!/bin/bash

# --
# Builds and uploads a release of the SDK against a release-specific tag.
# This does NOT create releases of individual services in the repo, just the SDK itself.
# --

syntax() {
    echo "Syntax: ./release.sh -r <tagname or 'snapshot'> [-f]"
    echo "Arguments:"
    echo "  -r: The version string of the SDK to be used in gradle and in the tag name, or '-r snapshot' to publish a snapshot of master."
    echo "  -f: Force the tag creation if the tag already exists. (not applicable to '-r snapshot')"
}

# Exit immediately on failure:
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

GRADLE_BUILD_FILE=$REPO_ROOT_DIR/build.gradle

FLAG_RELEASE=""
FLAG_FORCE="false"
while getopts "r:f" opt; do
    case $opt in
        r)
            FLAG_RELEASE=${OPTARG,,} # to lowercase
            ;;
        f)
            FLAG_FORCE="true"
            ;;
        \?)
            syntax
            exit 1
            ;;
    esac
done
if [ -z "$FLAG_RELEASE" ]; then
    echo "Missing required tag argument (-r <tagname or 'snapshot'>)"
    syntax
    exit 1
fi
echo "Settings: tag=$FLAG_RELEASE force=$FLAG_FORCE"

export SDK_VERSION=$(egrep "version = '(.*)'" $GRADLE_BUILD_FILE | cut -d "'" -f2)
if [ -z "$SDK_VERSION" ]; then
    echo "Unable to extract SDK version from $GRADLE_BUILD_FILE"
    exit 1
fi
echo "Current gradle version: $SDK_VERSION"

if [ "x${FLAG_RELEASE}" = "xsnapshot" ]; then
    # Sanity check: Validate that build.gradle already has a '-SNAPSHOT' version (case sensitive comparison)
    if [[ "$SDK_VERSION" != *"-SNAPSHOT"* ]]; then
        echo "SDK version in $GRADLE_BUILD_FILE doesn't contain '-SNAPSHOT'."
        echo "The master branch must only have snapshot builds."
        exit 1
    fi
else
    # Uploading a release: Create and publish the release tag, which contains a commit updating build.gradle.
    # Sanity check: Tagged releases of snapshots is disallowed (case insensitive comparison)
    if [[ "${FLAG_RELEASE^^}" = *"SNAPSHOT"* ]]; then
        echo "Requested tag release version may not contain 'SNAPSHOT'."
        exit 1
    fi
    sed -i "s/version = '${SDK_VERSION}'/version = '${FLAG_RELEASE}'/" $GRADLE_BUILD_FILE
    export SDK_VERSION=$FLAG_RELEASE
    git add $GRADLE_BUILD_FILE
    git commit -m "Automated cut of $FLAG_RELEASE"
    if [ "x$FLAG_FORCE" = "xtrue" ]; then
        git tag -f "$FLAG_RELEASE"
        git push origin -f "$FLAG_RELEASE"
    else
        FAILED=""
        git tag "$FLAG_RELEASE" || FAILED="true"
        # Jump through hoops to avoid conflicts with 'set -e':
        if [ -n "$FAILED" ]; then
            echo "Tag named '$FLAG_RELEASE' already exists locally. Use '-f' to overwrite the current tag."
            exit 1
        fi
        git push origin "$FLAG_RELEASE" || FAILED="true"
        if [ -n "$FAILED" ]; then
            echo "Tag named '$FLAG_RELEASE' already exists in remote repo. Use '-f' to overwrite the current tag."
            exit 1
        fi
    fi
fi

# Build bootstrap.zip and executor.zip before attempting to upload anything:
sdk/bootstrap/build.sh # produces sdk/bootstrap/bootstrap.zip
./gradlew :executor:distZip # produces sdk/executor/build/distributions/executor.zip

# Build and upload Java library to Maven repo (in prod S3):
./gradlew publish

# Upload built bootstrap.zip and executor.zip to directory in prod S3:
S3_DIR=s3://downloads.mesosphere.io/dcos-commons/artifacts/$SDK_VERSION
echo "Uploading bootstrap.zip and executor.zip to $S3_DIR"
aws s3 cp --acl public-read sdk/bootstrap/bootstrap.zip $S3_DIR/bootstrap.zip 1>&2
aws s3 cp --acl public-read sdk/executor/build/distributions/executor.zip $S3_DIR/executor.zip 1>&2
