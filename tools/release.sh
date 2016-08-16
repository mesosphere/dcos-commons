#!/bin/bash

# Exit immediately on failure:
set -e

REPO_ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $REPO_ROOT_DIR

# Upload current tools (with '.commit' file containing the current SHA) to DEV S3.
# This can be downloaded via: https://s3-us-west-2.amazonaws.com/infinity-artifacts/dcos-commons-tools.tgz

if [ -n "${TOOLS_AWS_SECRET_ACCESS_KEY}" ]; then
    AWS_SECRET_ACCESS_KEY=${TOOLS_AWS_SECRET_ACCESS_KEY}
fi
if [ -n "${TOOLS_AWS_ACCESS_KEY_ID}" ]; then
    AWS_ACCESS_KEY_ID=${TOOLS_AWS_ACCESS_KEY_ID}
fi

rm -rf dcos-commons-tools/
mkdir -p dcos-commons-tools/
echo "$(git rev-parse HEAD)" > dcos-commons-tools/.commit
cp *.py dcos-commons-tools/
tar czvf dcos-commons-tools.tgz dcos-commons-tools/
rm -rf dcos-commons-tools/
aws s3 cp --acl public-read ./dcos-commons-tools.tgz s3://infinity-artifacts/dcos-commons-tools.tgz
rm dcos-commons-tools.tgz
