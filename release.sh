#!/bin/bash

# --
# Builds and uploads a release of the SDK against a release-specific tag.
# This does NOT create releases of individual services in the repo, just the SDK itself.
# --

syntax() {
    echo "Syntax: ./release.sh -r <tagname or 'snapshot'> [-f] [-d]"
    echo "Arguments:"
    echo "  -r: The version string of the SDK to be used in gradle and in the tag name, or '-r snapshot' to publish a snapshot of master."
    echo "  -f: Force the tag creation if the tag already exists. (not applicable to '-r snapshot')"
    echo "  -d: Dry run. Build artifacts but do not upload/publish them."
}

# Exit immediately on failure:
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

GRADLE_BUILD_FILE=$REPO_ROOT_DIR/build.gradle

FLAG_RELEASE=""
FLAG_FORCE="false"
FLAG_DRY_RUN="false"
while getopts "r:fd" opt; do
    case $opt in
        r)
            FLAG_RELEASE=${OPTARG,,} # to lowercase
            ;;
        f)
            FLAG_FORCE="true"
            ;;
        d)
            FLAG_DRY_RUN="true"
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
echo "Settings: tag=$FLAG_RELEASE force=$FLAG_FORCE dry_run=$FLAG_DRY_RUN"

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
    if [ "x$FLAG_DRY_RUN" = "xfalse" ]; then
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
fi

# Perform all builds before attempting to upload anything

sdk/bootstrap/build.sh # produces sdk/bootstrap/bootstrap.zip
sdk/cli/build.sh # produces sdk/cli/[dcos-service-cli-linux, dcos-service-cli-darwin, dcos-service-cli.exe]
./gradlew :executor:distZip # produces sdk/executor/build/distributions/executor.zip
rm -f tools/pip/*.whl
tools/pip/build.sh $SDK_VERSION # produces tools/pip/[testing|tools]-[SDK_VERSION (with modifications)]-py3-none-any.whl

# Collect the other artifacts into a single directory, calculate a SHA256SUMS file, and then upload the lot.
# The SHA256SUMS file is required for construction of packages that use default CLIs, and is a Good Idea in general.
echo "Collecting artifacts in $(pwd)/$SDK_VERSION"
rm -rf $SDK_VERSION
mkdir $SDK_VERSION

# Note about .whl files: pip is picky about version formatting, so the outputted filenames may not exactly match $SDK_VERSION due to some mangling.
# For example, "1.2.3-SNAPSHOT" has to be converted to "1.2.3+snapshot".
# Additionally, pip will refuse to install .whl files without the extra info in the filename, so we leave all that in.

cp sdk/bootstrap/bootstrap.zip \
   sdk/executor/build/distributions/executor.zip \
   sdk/cli/dcos-service-cli* \
   tools/pip/*.whl \
   $SDK_VERSION/

# Ensure that checksums do not contain relative dir path:
cd $SDK_VERSION/
sha256sum * | tee SHA256SUMS # print stdout+stderr, write stdout to file
ls -l
cd ..

S3_DIR=s3://downloads.mesosphere.io/dcos-commons/artifacts/$SDK_VERSION
if [ "x$FLAG_DRY_RUN" != "xfalse" ]; then
    echo "Dry run: Exiting without uploading artifacts to $S3_DIR"
    exit 0
fi

echo "Uploading artifacts to $S3_DIR"

# Build+upload Java libraries to Maven repo (within S3, see build.gradle):
./gradlew publish

# Upload contents of artifact dir:
aws s3 sync --acl public-read $SDK_VERSION/ $S3_DIR 1>&2

# Clean up
rm -rf $SDK_VERSION
