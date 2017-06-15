#!/bin/bash
random_id=$(cat /dev/urandom | tr -dc 'a-z0-9' | fold -w 10 | head -n 1)
set -e -x
# Create cluster
cat <<EOF > config.yaml
launch_config_version: 1
deployment_name: dcos-ci-test-infinity-$random_id
template_url: https://s3.amazonaws.com/downloads.mesosphere.io/dcos-enterprise/testing/master/cloudformation/ee.single-master.cloudformation.json
provider: aws
aws_region: us-west-2
key_helper: true
template_parameters:
    AdminLocation: 0.0.0.0/0
    PublicSlaveInstanceCount: 1
    SlaveInstanceCount: 6
ssh_user: core
EOF

# launch our test cluster and let it provision in the background while we build
# dcos-launch create

# Build and upload our framework
FRAMEWORK=$1
export UNIVERSE_URL_PATH=/dcos-commons/frameworks/$FRAMEWORK/$FRAMEWORK-universe-url
/dcos-commons/frameworks/$FRAMEWORK/./build.sh aws
if [ ! -f "$UNIVERSE_URL_PATH" ]; then
    echo "Missing universe URL file: $UNIVERSE_URL_PATH"
    exit 1
fi

# Wait for our cluster to finish
dcos-launch wait
# Setup the SSH key for shakedown to use
mkdir -p ~/.ssh/
cat cluster_info.json | jq -r .ssh_private_key > ~/.ssh/id_rsa
chmod 600 ~/.ssh/id_rsa

# configure the dcos-cli/shakedown-backend
CLUSTER_URL=http://`dcos-launch describe | jq -r .masters[0].public_ip`
dcos config set core.dcos_url $CLUSTER_URL
dcos config set core.ssl_verify false
/dcos-commons/tools/./dcos_login.py
for url in `cat $UNIVERSE_URL_PATH`; do
    dcos package repo add --index=0 `echo $url | cut -d / -f 5` $url
done

set +e
py.test --teamcity -vv -s -m "sanity and not azure" /dcos-commons/frameworks/$FRAMEWORK/tests
dcos-launch delete
