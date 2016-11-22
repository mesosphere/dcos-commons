#!/bin/bash

# capture anonymous metrics for reporting
curl https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/create-dev-env-start.png >/dev/null 2>&1

# abort script at first error:
set -e

error_msg() {
    echo "---"
    echo "Failed to build the cluster: Exited early at $0:L$1"
    echo "To try again, re-run this script."
    echo "---"
}
trap 'error_msg ${LINENO}' ERR

DCOS_VERSION=${DCOS_VERSION:="stable"}
AGENTS=${AGENTS:=3}
CLUSTER_URL=http://172.17.0.2

ARTIFACT_BOX_BASE=dcos-centos-virtualbox-0.8.0.box
ARTIFACT_DCOS_INSTALLER=dcos_generate_config.sh
ARTIFACT_GOLANG=go1.7.3.linux-amd64.tar.gz
ARTIFACT_UPX=upx-3.91-amd64_linux.tar.bz2
ARTIFACT_PIP=get-pip.py
ARTIFACT_DCOSCLI=dcos
ARTIFACT_JDK=jdk-8u112-linux-x64.tar.gz

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR

if [ ! -d "dcos-docker" ]; then
    git clone https://github.com/NimaVaziri/dcos-docker.git
fi
cd dcos-docker/
DCOS_DOCKER_DIR=$(pwd)

# Manually fetch/install base box: Allow on-disk caching of box file (usb stick scenario)
if [ $(vagrant box list | grep mesosphere/dcos-centos-virtualbox | wc -l) -eq 0 ]; then
    echo "### Downloading base box image"
    if [ ! -f ${ARTIFACT_BOX_BASE} ]; then
        curl -O https://downloads.dcos.io/dcos-vagrant/${ARTIFACT_BOX_BASE}
    fi
    vagrant box add --name mesosphere/dcos-centos-virtualbox ${ARTIFACT_BOX_BASE}
fi

if [ ! -f ${ARTIFACT_DCOS_INSTALLER} ]; then
    echo "### Downloading DC/OS ${DCOS_VERSION} installer"
    curl -O https://downloads.dcos.io/dcos/${DCOS_VERSION}/${ARTIFACT_DCOS_INSTALLER}
fi

echo "### Destroying pre-existing VM, if any"
vagrant destroy

echo "### Building VM"
DCOS_BOX_URL="file:/$(pwd)/${ARTIFACT_BOX_BASE}" vagrant/resize-disk.sh ${VM_DISK_SIZE:=20480}

echo "### Launching cluster and installing tools in VM"
# Note: every variable that isn't escaped with a backslash is injected from THIS script.
cat > setup.sh <<EOF
#!/bin/bash
set -e

error_msg() {
    echo "Failed to build the cluster: Exited early at \$0:\$1"
}
trap 'error_msg \${LINENO}' ERR

echo '### Launch DC/OS Cluster with ${AGENTS} agents' &&
cd /vagrant/ &&
rm -f dcos-genconf.* &&
make EXTRA_GENCONF_CONFIG="oauth_enabled: false" PUBLIC_AGENTS=0 AGENTS=${AGENTS} &&

echo '### Install git/nano' &&
yes | sudo yum install git nano &&
sudo yum clean all &&

echo '### Install Golang' &&
if [ ! -f ${ARTIFACT_GOLANG} ]; then
  curl -O https://storage.googleapis.com/golang/${ARTIFACT_GOLANG}
fi &&
sudo tar -C /usr/local -xzf ${ARTIFACT_GOLANG} &&
sudo ln -s /usr/local/go/bin/go /usr/local/bin/go &&

echo '### Install upx' &&
if [ ! -f ${ARTIFACT_UPX} ]; then
    curl -O http://upx.sourceforge.net/download/${ARTIFACT_UPX}
fi &&
tar xf ${ARTIFACT_UPX} &&
sudo mv upx-3.91-amd64_linux/upx /usr/local/bin &&
rm -rf upx-*/ &&

echo '### Install pip' &&
if [ ! -f ${ARTIFACT_PIP} ]; then
    curl -O https://bootstrap.pypa.io/${ARTIFACT_PIP}
fi &&
sudo python ${ARTIFACT_PIP} &&

echo '### Install virtualenv' &&
sudo pip install virtualenv &&

echo '### Install DC/OS CLI' &&
if [ ! -f ${ARTIFACT_DCOSCLI} ]; then
    curl -O https://downloads.dcos.io/binaries/cli/linux/x86-64/latest/${ARTIFACT_DCOSCLI}
fi &&
sudo mv ${ARTIFACT_DCOSCLI} /usr/local/bin &&
sudo chmod +x /usr/local/bin/${ARTIFACT_DCOSCLI} &&

echo '### Install JDK' &&
if [ ! -f ${ARTIFACT_JDK} ]; then
    curl -O https://downloads.mesosphere.com/java/${ARTIFACT_JDK}
fi &&
sudo tar -C /opt -xzf ${ARTIFACT_JDK} &&

echo '### Configure env' &&
echo 'export GOPATH=/home/vagrant/go' >> ~/.bash_profile &&
echo 'export JAVA_HOME=\$(echo /opt/jdk*)' >> ~/.bash_profile &&
echo 'export PATH=/home/vagrant:\$JAVA_HOME/bin:/usr/local/bin:\$PATH' >> ~/.bash_profile &&
. ~/.bash_profile &&

echo '### Point CLI to cluster' &&
dcos config set core.dcos_url ${CLUSTER_URL} &&

echo '### Configure cluster SSH key' &&
mkdir -p /home/vagrant/.ssh &&
chmod 700 /home/vagrant/.ssh &&
cp /vagrant/genconf/ssh_key /home/vagrant/.ssh/id_rsa &&
chmod 600 /home/vagrant/.ssh/id_rsa &&

echo '### Wait for cluster to finish coming up' &&
make postflight
EOF
chmod +x setup.sh
vagrant ssh -c "/vagrant/setup.sh"

echo "### Copying start-dcos.sh into image"
cat > start-dcos.sh <<EOF
#!/bin/bash

STOPPED_MASTERS=\$(docker ps -a -f status=exited | awk '{print \$NF}' | grep dcos-docker-master | sort)
STOPPED_PUB_AGENTS=\$(docker ps -a -f status=exited | awk '{print \$NF}' | grep dcos-docker-pubagent | sort)
STOPPED_PRIV_AGENTS=\$(docker ps -a -f status=exited | awk '{print \$NF}' | grep dcos-docker-agent | sort)

restart_nodes() {
    for node in \$1; do
        docker start \$node
        sleep 2
    done
}

echo "Launching \$(echo \$STOPPED_MASTERS | wc -w) master(s)"
restart_nodes "\$STOPPED_MASTERS"

# mystery: need to kick 'make' before starting agents, or else this happens:
# dcos-docker-agent1 mesos-agent[3545]:   3546 systemd.cpp:325] Started systemd slice 'mesos_executors.slice'
# dcos-docker-agent1 mesos-agent[3545]: Failed to initialize systemd: Failed to locate systemd cgroups hierarchy: does not exist

NUM_STOPPED_PUB_AGENTS=\$(echo \$STOPPED_PUB_AGENTS | wc -w)
if [ \$NUM_STOPPED_PUB_AGENTS -eq 0 ]; then
    echo "No public agents to launch"
else
    echo "Launching \$NUM_STOPPED_PUB_AGENTS public agents"
    PUBLIC_AGENTS=\$NUM_STOPPED_PUB_AGENTS make -C dcos-docker/ public_agent
    restart_nodes "\$STOPPED_PUB_AGENTS"
fi

NUM_STOPPED_PRIV_AGENTS=\$(echo \$STOPPED_PRIV_AGENTS | wc -w)
if [ \$NUM_STOPPED_PRIV_AGENTS -eq 0 ]; then
    echo "No private agents to launch"
else
    echo "Launching \$NUM_STOPPED_PRIV_AGENTS private agents"
    AGENTS=\$NUM_STOPPED_PRIV_AGENTS make -C dcos-docker/ agent
    restart_nodes "\$STOPPED_PRIV_AGENTS"
fi
EOF
chmod +x start-dcos.sh
# copy makefile (+ file dependency) from dcos-docker repo for 'kick':
vagrant ssh -c "cp /vagrant/start-dcos.sh /vagrant/Makefile /vagrant/common.mk ~"

${SCRIPT_DIR}/node-route.sh

echo "----"
echo "Dashboard URL:  ${CLUSTER_URL}"
echo ""
echo "Log into VM:    pushd ${DCOS_DOCKER_DIR} && vagrant ssh && popd"
echo "Build example:  Log into VM, then: cd /dcos-commons/frameworks/helloworld && ./build.sh local"
echo ""
echo "Rebuild routes: ${SCRIPT_DIR}/node-route.sh"
echo "Delete VM:      pushd ${DCOS_DOCKER_DIR} && vagrant destroy && popd"
echo "Delete data:    rm -rf ${DCOS_DOCKER_DIR}"
echo "---"

# capture anonymous metrics for reporting
curl https://mesosphere.com/wp-content/themes/mesosphere/library/images/assets/sdk/create-dev-env-finish.png >/dev/null 2>&1

