#!/bin/bash

#
# This script creates the packages needed to run dcos-tests against a build with ci-test.sh, and
# updates docker-context/custom-universes.txt to point to those packages.
#
# THIS SCRIPT IS THE ENTRYPOINT FOR SETTING UP BUILD ARTIFACTS
#
# When running this script, the user must provide the following args:
# 1) The name of the universe package, eg "kafka" or "cassandra".
# 2) The path to the 'draft' universe package contents (dir with .json files).
# 3...N) The paths to the build artifact files which should be uploaded to S3 and referenced by a
#        stub universe
#

# Paths:
TOOLS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# AWS settings:
if [ -z "$AWS_UPLOAD_REGION" ]; then
    AWS_UPLOAD_REGION="us-west-2"
fi
if [ -z "$S3_BASE_PATH" ]; then
    S3_BASE_PATH="infinity-artifacts/autodelete7d"
fi

syntax() {
    echo "Syntax: $0 <package-name> <template-package-dir> [upload-file1] [upload-file2] [upload-file3] ..."
    echo "  Example: $ $0 kafka /path/to/template/jsons/ /path/to/kafka-framework-1.2.3.jar /path/to/kafka-4.5.6.zip"
}
run_cmd() {
    echo "$@"
    $@
}

PACKAGE_NAME=$1
shift
PACKAGE_SRC_DIR=$1
shift
UPLOAD_FILES=$@

# automatically falls back to printing if we're not in CI:
notify_github() {
    run_cmd ${TOOLS_DIR}/github_update.py $1 "upload:$PACKAGE_NAME" $2
}

if [ -n "$JENKINS_HOME" ]; then
    # we're in a CI build. configure a properties output containing the upload URIs:
    PROPERTIES_FILE="${WORKSPACE}/stub-universe.properties"
fi

notify_github pending "Uploading artifacts and stub universe, creating test env"

# Validate args were all present:
if [ -z "$UPLOAD_FILES" ]; then
    notify_github error "Missing arguments to ci-build.sh"
    syntax
    exit 1
fi
# Validate upload files all exist:
for UPLOAD_FILE in $UPLOAD_FILES; do
    if [ ! -f "$UPLOAD_FILE" ]; then
        echo "Provided file to upload not found: $UPLOAD_FILE"
        notify_github error "Upload file not found: $UPLOAD_FILE"
        exit 1
    fi
done

# Generate an S3 path for uploads:
DATETIME=$(date +"%Y%m%d-%H%M%S")
UUID=$(cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 16 | head -n 1)
S3_PATH="${PACKAGE_NAME}/${DATETIME}-${UUID}"
S3_PATH_URL="https://s3-${AWS_UPLOAD_REGION}.amazonaws.com/${S3_BASE_PATH}/${S3_PATH}"
echo "### Using S3 path: $S3_PATH_URL"

# Create stub universe package against generated S3 path:
echo "### Creating stub universe.."
STUB_UNIVERSE_SCRIPT="universe_builder.py"
echo "### $STUB_UNIVERSE_SCRIPT START"
TEMP_STDOUT=$(mktemp)
run_cmd python ${TOOLS_DIR}/${STUB_UNIVERSE_SCRIPT} "$PACKAGE_NAME" "stub-universe" "$PACKAGE_SRC_DIR" "$S3_PATH_URL" $UPLOAD_FILES >> $TEMP_STDOUT
RET=$?
cat $TEMP_STDOUT
echo "### $STUB_UNIVERSE_SCRIPT END"
if [ $RET -ne 0 ]; then
    echo "$STUB_UNIVERSE_SCRIPT failed, exiting"
    notify_github error "Failed to create stub universe"
    exit 1
fi
UNIVERSE_ZIP_FILE=$(tail -n 1 $TEMP_STDOUT)
echo "### Created stub universe: $UNIVERSE_ZIP_FILE"
rm -f $TEMP_STDOUT

# Upload stub universe and any provided build artifacts to generated S3 path:
STUB_UNIVERSE_S3_DIR="s3://${S3_BASE_PATH}/${S3_PATH}"
upload_to_aws() {
    run_cmd "aws s3 --region=$AWS_UPLOAD_REGION cp --acl public-read $1 ${STUB_UNIVERSE_S3_DIR}/$(basename $1)"
    if [ $? -ne 0 ]; then
        echo "Failed to upload $1, exiting"
        notify_github error "Failed to upload $1"
        exit 1
    fi
}
# (upload: stub universe zip, then all requested files)
for UPLOAD_FILE in $UNIVERSE_ZIP_FILE $UPLOAD_FILES; do
    echo "### Uploading [$UPLOAD_FILE] to [${S3_PATH_URL}/$(basename $UPLOAD_FILE)]"
    upload_to_aws $UPLOAD_FILE
done
UNIVERSE_ZIP_URL="${S3_PATH_URL}/$(basename $UNIVERSE_ZIP_FILE)"

# Output the resulting stub universe's URL to various places...

# stdout:
echo "#####"
echo "Uploaded custom universe:"
echo "$UNIVERSE_ZIP_URL"
echo "#####"
# custom-universes.txt (used by dcos-tests):
if [ -n "$CUSTOM_UNIVERSES_PATH" ]; then
    echo "stub $UNIVERSE_ZIP_URL" > $CUSTOM_UNIVERSES_PATH
fi
# stub-universe.properties (used by CI):
if [ -n "$PROPERTIES_FILE" ]; then
    # http://path/to/randtok/stub-universe.zip
    echo "STUB_UNIVERSE_URL=$UNIVERSE_ZIP_URL" > $PROPERTIES_FILE
    # s3://path/to/randtok
    echo "STUB_UNIVERSE_S3_DIR=$STUB_UNIVERSE_S3_DIR" >> $PROPERTIES_FILE
fi
# custom 'Details' link in github status (used by CI):
GITHUB_COMMIT_STATUS_URL=$UNIVERSE_ZIP_URL notify_github success "Uploaded $PACKAGE_NAME artifacts and stub universe"
