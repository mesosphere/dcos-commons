#!/bin/bash

set -e
source ${TOOLS_DIR}/init_paths.sh

S3_BUCKET=${S3_BUCKET:=infinity-artifacts}
SDK_VERSION=${SDK_VERSION:=stub}
PREFIX_PATH=artifacts/$SDK_VERSION
EXECUTOR_ZIP=${REPO_ROOT_DIR}/sdk/executor/build/distributions/executor.zip
BOOTSTRAP_ZIP=${REPO_ROOT_DIR}/sdk/bootstrap/bootstrap.zip

echo "Uploading executor.zip to: https://downloads.mesosphere.com/$S3_BUCKET/$PREFIX_PATH/executor.zip"
aws s3 cp --acl public-read $EXECUTOR_ZIP s3://$S3_BUCKET/$PREFIX_PATH/executor.zip 1>&2
echo "Uploaded executor.zip to: https://downloads.mesosphere.com/$S3_BUCKET/$PREFIX_PATH/executor.zip"

echo "Uploading bootstrap.zip to: https://downloads.mesosphere.com/$S3_BUCKET/$PREFIX_PATH/bootstrap.zip"
aws s3 cp --acl public-read $BOOTSTRAP_ZIP s3://$S3_BUCKET/$PREFIX_PATH/bootstrap.zip 1>&2
echo "Uploaded bootstrap.zip to: https://downloads.mesosphere.com/$S3_BUCKET/$PREFIX_PATH/bootstrap.zip"
