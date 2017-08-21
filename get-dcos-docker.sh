#!/bin/bash

# Launches a DC/OS dev environment based containing a running 3-agent cluster and dev tools.
#
# Prerequisites:
# - 8GB RAM
# - Vagrant
# - Virtualbox
#
# Arguments:
# - BOX_PATH: Local path to use for .box image (useful if installing from USB stick)

# abort script at first error:
set -e

BOX_NAME=mesosphere/dcos-docker-sdk

error_msg() {
    echo "---"
    echo "Failed to build the cluster: Exited early at $0:L$1"
    echo "To try again, re-run this script."
    echo "---"
}
trap 'error_msg ${LINENO}' ERR

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR

cd tools/vagrant/
VAGRANT_DIR=$(pwd)

# Image-on-USB-stick scenario: Manually add the provided .box file directly
if [ $(vagrant box list | grep $BOX_NAME | wc -l) -eq 0 ]; then
    if [ -n "$BOX_PATH" ]; then
        echo "### Installing provided local box image as $BOX_NAME: $BOX_PATH"
        vagrant box add --name $BOX_NAME ${BOX_PATH}
    fi
elif [ -n "$BOX_PATH" ]; then
    echo "### NOTICE: Local box image was provided, but vagrant already has a box named $BOX_NAME. Ignoring provided image: $BOX_PATH"
fi

echo "### Destroying pre-existing VM, if any"
vagrant destroy # intentionally allowing confirmation prompt in case the user didn't actually want to do this

echo "### Bringing up VM"
vagrant up

echo "### Starting DC/OS environment within VM"
vagrant ssh -c "/home/vagrant/start-dcos.sh"

echo "### Waiting for cluster to finish coming up"
vagrant ssh -c "docker exec -i dcos-docker-master1 dcos-postflight"

${SCRIPT_DIR}/node-route.sh

echo "----"
echo "Dashboard URL:  http://172.17.0.2"
echo ""
echo "Log into VM:    pushd ${VAGRANT_DIR} && vagrant ssh && popd"
echo "Build example:  Log into VM, then: cd /dcos-commons/frameworks/helloworld && ./build.sh local"
echo ""
echo "Repair routes:  ${SCRIPT_DIR}/node-route.sh # (use this if VM connectivity is lost)"
echo "Delete VM/data: pushd ${VAGRANT_DIR} && vagrant destroy && vagrant box remove $BOX_NAME && popd"
echo "---"
