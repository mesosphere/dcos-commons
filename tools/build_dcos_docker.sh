#!/bin/bash

DCOS_VERSION=${DCOS_VERSION:="stable"}
AGENTS=${AGENTS:=3}

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd $SCRIPT_DIR

if [ "$(basename $(pwd))" != "dcos-docker" ]; then
    if [ ! -d "dcos-docker" ]; then
        git clone https://github.com/dcos/dcos-docker.git
    fi
    cd dcos-docker/
fi
VAGRANT_DIR=$(pwd)

if [ ! -f "dcos_generate_config.sh" ]; then
    curl -O https://downloads.dcos.io/dcos/${DCOS_VERSION}/dcos_generate_config.sh
fi

echo "### Destroying pre-existing VM, if any"
vagrant destroy

echo "### Building VM"
vagrant/resize-disk.sh 20480

echo "### Launching cluster and installing tools in VM"
vagrant ssh <<- EOF
  echo '### Launch DC/OS Cluster with ${AGENTS} agents' &&
  cd /vagrant/ &&
  rm -f dcos-genconf.* &&
  make PUBLIC_AGENTS=0 AGENTS=${AGENTS} &&
  echo '### Install Git/Golang' &&
  yes | sudo yum install git golang &&
  yum clean all &&
  echo '### Install UPX' &&
  if [ ! -f upx-3.91-amd64_linux.tar.bz2 ]; then
    curl -O http://upx.sourceforge.net/download/upx-3.91-amd64_linux.tar.bz2
  fi &&
  tar xf upx-3.91-amd64_linux.tar.bz2 &&
  sudo mv upx-3.91-amd64_linux/upx /usr/local/bin &&
  rm -rf upx-*/ &&
  echo '### Install pip' &&
  curl https://bootstrap.pypa.io/get-pip.py | sudo python &&
  echo '### Install DC/OS CLI' &&
  curl https://downloads.dcos.io/binaries/cli/linux/x86-64/latest/dcos > /home/vagrant/dcos &&
  chmod +x /home/vagrant/dcos &&
  echo '### Install JDK' &&
  if [ ! -f jdk-8u112-linux-x64.tar.gz ]; then
    curl -O https://downloads.mesosphere.com/java/jdk-8u112-linux-x64.tar.gz
  fi &&
  cd /opt &&
  rm -rf jdk*/ &&
  sudo tar xf /vagrant/jdk-8u112-linux-x64.tar.gz &&
  echo '### Configure env' &&
  echo 'export GOPATH=/home/vagrant/go' >> ~/.bash_profile &&
  echo 'export JAVA_HOME=\$(echo /opt/jdk*)' >> ~/.bash_profile &&
  echo 'export PATH=/home/vagrant:\$JAVA_HOME/bin:\$PATH' >> ~/.bash_profile &&
  echo 'eval \$(ssh-agent -s)' >> ~/.bash_profile &&
  echo 'ssh-add /vagrant/genconf/ssh_key' >> ~/.bash_profile &&
  echo 'cd /vagrant/' >> ~/.bash_profile &&
  . ~/.bash_profile &&
  echo '### Wait for Cluster to finish coming up' &&
  make postflight &&
  echo '### Point CLI to Cluster' &&
  dcos config set core.dcos_url http://172.17.0.2
EOF

echo "### Configuring routing from host to DC/OS Master"
# "uname -a" samples:
# OSX: Darwin mesospheres-MacBook-Pro-2.local 15.6.0 Darwin Kernel Version 15.6.0: Mon Aug 29 20:21:34 PDT 2016; root:xnu-3248.60.11~1/RELEASE_X86_64 x86_64
# Linux: Linux augustao 4.2.0-42-generic #49-Ubuntu SMP Tue Jun 28 21:26:26 UTC 2016 x86_64 x86_64 x86_64 GNU/Linux
KERNEL=$(uname -a | awk '{print $1}')
if [ "$KERNEL" = "Linux" ]; then
    sudo ip route replace 172.17.0.0/16 via 192.168.65.50
elif [ "$KERNEL" = "Darwin" ]; then
    sudo route -nv add -net 172.17.0.0/16 192.168.65.50
else
    echo "Unknown kernel for route configuration: $KERNEL (from '$(uname -a)')"
fi

#echo "### Fetching/building SDK in VM"
#vagrant ssh <<- EOF
#  git clone https://github.com/mesosphere/dcos-commons.git
#  && cd dcos-commons/
#  && ./gradlew check
#EOF

echo "----"
echo "Dashboard URL:   http://172.17.0.2"
echo "Log into VM:     cd ${VAGRANT_DIR} && vagrant ssh"
echo "Package VM .box: vagrant package --base dcos-docker --output dcos-docker-sdk.box"
#echo "Local SDK Path:  ${VAGRANT_DIR}/dcos-commons"
#echo "Build SDK in VM: Log into VM, then: cd /vagrant/dcos-commons && ./gradlew check"
echo "---"
