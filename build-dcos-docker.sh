#!/bin/bash

DCOS_VERSION=${DCOS_VERSION:="stable"}
AGENTS=${AGENTS:=3}

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR

if [ ! -d "dcos-docker" ]; then
    git clone https://github.com/NimaVaziri/dcos-docker.git
fi
cd dcos-docker/
DCOS_DOCKER_DIR=$(pwd)

if [ ! -f dcos-centos-virtualbox-0.8.0.box ]; then
    curl -O https://downloads.dcos.io/dcos-vagrant/dcos-centos-virtualbox-0.8.0.box
fi
vagrant box add --name mesosphere/dcos-centos-virtualbox dcos-centos-virtualbox-0.8.0.box

if [ ! -f dcos_generate_config.sh ]; then
    curl -O https://downloads.dcos.io/dcos/${DCOS_VERSION}/dcos_generate_config.sh
fi

echo "### Destroying pre-existing VM, if any"
vagrant destroy

echo "### Building VM"
#DCOS_BOX_VERSION="" DCOS_BOX_URL="" \
vagrant/resize-disk.sh 20480

echo "### Launching cluster and installing tools in VM"
vagrant ssh <<- EOF
  echo '### Launch DC/OS Cluster with ${AGENTS} agents' &&
  cd /vagrant/ &&
  rm -f dcos-genconf.* &&
  make PUBLIC_AGENTS=0 AGENTS=${AGENTS} &&

  echo '### Install git/nano' &&
  yes | sudo yum install git nano &&
  sudo yum clean all &&

  echo '### Install Golang' &&
  if [ ! -f go1.7.3.linux-amd64.tar.gz ]; then
    curl -O https://storage.googleapis.com/golang/go1.7.3.linux-amd64.tar.gz
  fi &&
  sudo tar -C /usr/local -xzf go1.7.3.linux-amd64.tar.gz &&
  sudo ln -s /usr/local/go/bin/go /usr/local/bin/go &&

  echo '### Install UPX' &&
  if [ ! -f upx-3.91-amd64_linux.tar.bz2 ]; then
      curl -O http://upx.sourceforge.net/download/upx-3.91-amd64_linux.tar.bz2
  fi &&
  tar xf upx-3.91-amd64_linux.tar.bz2 &&
  sudo mv upx-3.91-amd64_linux/upx /usr/local/bin &&
  rm -rf upx-*/ &&

  echo '### Install pip' &&
  if [ ! -f get-pip.py ]; then
      curl -O https://bootstrap.pypa.io/get-pip.py
  fi &&
  sudo python get-pip.py &&

	echo '### Install virtualenv' &&
	sudo pip install virtualenv &&

  echo '### Install DC/OS CLI' &&
  if [ ! -f dcos ]; then
      curl -O https://downloads.dcos.io/binaries/cli/linux/x86-64/latest/dcos
  fi &&
  cp dcos /home/vagrant/dcos && chmod +x /home/vagrant/dcos &&

  echo '### Install JDK' &&
  if [ ! -f jdk-8u112-linux-x64.tar.gz ]; then
      curl -O https://downloads.mesosphere.com/java/jdk-8u112-linux-x64.tar.gz
  fi &&
  sudo tar -C /opt -xzf jdk-8u112-linux-x64.tar.gz &&

  echo '### Configure env' &&
  echo 'export GOPATH=/home/vagrant/go' >> ~/.bash_profile &&
  echo 'export JAVA_HOME=\$(echo /opt/jdk*)' >> ~/.bash_profile &&
  echo 'export PATH=/home/vagrant:\$JAVA_HOME/bin:/usr/local/bin:\$PATH' >> ~/.bash_profile &&
  echo 'cd /vagrant/' >> ~/.bash_profile &&
  . ~/.bash_profile &&

  echo '### Point CLI to Cluster' &&
  dcos config set core.dcos_url http://172.17.0.2 &&

  echo '### Configure cluster SSH key' &&
  mkdir -p /home/vagrant/.ssh &&
  chmod 700 /home/vagrant/.ssh &&
  cp /vagrant/genconf/ssh_key /home/vagrant/.ssh/id_rsa &&
  chmod 600 /home/vagrant/.ssh/id_rsa &&

  echo '### Wait for Cluster to finish coming up' &&
  make postflight
EOF

${SCRIPT_DIR}/node-route.sh

echo "----"
echo "Dashboard URL:  http://172.17.0.2"
echo "Log into VM:    cd ${DCOS_DOCKER_DIR} && vagrant ssh"
echo "Build example:  Log into VM, then: cd /dcos-commons/frameworks/helloworld && ./build.sh local"
echo "---"
